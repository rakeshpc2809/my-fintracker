# Detailed Design Specification: Tax Ledger & Portfolio Cockpit

**Version**: 2.0  
**Status**: Initial Architecture & Package Design  
**Reference Document**: [tax-ledger-hld-v2.md](file:///home/rakeshpc/Projects/my-fintracker/tax-ledger-hld-v2.md)

---

## 1. System Overview & Bounded Contexts

```
                       ┌─────────────────────────────────────┐
                       │          Statement Sources          │
                       │    CAS PDF / Broker P&L CSVs        │
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
                       ┌─────────────────────────────────────┐
                       │           parsers (Python)          │
                       │   CasParser · BrokerCsvParser       │
                       │  Signed Unit-Delta Pre-Flight Check │
                       └──────────────────┬──────────────────┘
                                          │  TaxEvent Stream
                                          ▼
                       ┌─────────────────────────────────────┐
                       │      tax-adapter-api (Ktor)         │
                       │     Ingestion & Report Routes       │
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           tax-core (Pure Kotlin)                            │
│  ReconciliationGate · FifoMatcher · TaxClassifier · ExemptionTracker        │
│  — Pure domain rules, zero I/O, versioned FY YAML rule-set evaluation —    │
└─────────────────────────────────────────┬───────────────────────────────────┘
                                          │
                                          ▼
                       ┌─────────────────────────────────────┐
                       │   tax-adapter-persistence (Postgres)│
                       │  tax_events (Append-only Event Log) │
                       │  open_lots (Materialized View)      │
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
                       ┌─────────────────────────────────────┐
                       │    valuation-engine (Kotlin)        │
                       │ AMFI EOD NAV Sync · Stock Feed Sync │
                       │ XIRR Engine · Decision Radar        │
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
                       ┌─────────────────────────────────────┐
                       │        web-cockpit (Web UI)         │
                       │   Net Worth · LTCG Exemption Meter  │
                       │  Decision Radar · Harvest Advisor   │
                       └─────────────────────────────────────┘
```

---

## 2. Package Specifications & Goals

### 2.1 Package: `tax-core`

* **Directory Location**: `tax-domain/tax-core`
* **Technology**: Pure Kotlin 2.x (JDK 21, Gradle)
* **Dependencies**: Kotlin standard library, `kotlinx-datetime`, `jackson-dataformat-yaml` (or custom rule model). No DB, HTTP, or framework dependencies.

#### **Goals & Responsibilities**:
1. **Domain Data Models**:
   - `TaxEvent`: UUID, `assetId`, `eventType`, `eventDate`, `units`, `pricePerUnit`, `grossAmount`, `sourceDocumentId`, `ingestedAt`.
   - `EventType`: `ACQUISITION`, `SIP_INSTALMENT`, `DISPOSAL`, `BONUS`, `SPLIT`, `DIVIDEND_REINVEST`, `SGB_INTEREST`, `SGB_MATURITY`, `MERGER`.
   - `Lot`: Open inventory lot of asset units (`lotId`, `assetId`, `acquisitionDate`, `originalUnits`, `remainingUnits`, `costPerUnit`, `totalCostBasis`).
   - `MatchedLot`: Association between a `DISPOSAL` event and one or more `Lot` allocations, holding period calculation, gain amount, and tax classification (`STCG` vs `LTCG`).

2. **Reconciliation Gate**:
   - Implements signed unit-delta validation:
     $$\text{unitDelta}(e) = \begin{cases} -e.\text{units} & \text{if } e.\text{eventType} = \text{DISPOSAL} \\ 0 & \text{if } e.\text{eventType} = \text{SGB\_INTEREST} \\ e.\text{units} & \text{otherwise} \end{cases}$$
   - Validates that $\sum \text{unitDelta}(e) = \text{declaredClosingUnits}$ before committing events to ledger.

3. **Non-Cash Event Reallocation**:
   - **BONUS**: Creates zero-cost lot ($cost = 0$). Holding period clock starts on allotment date.
   - **SPLIT**: Re-denominates open lots for that asset ($units' = units \times ratio$, $costPerUnit' = \frac{costPerUnit}{ratio}$). Preserves acquisition date and total cost basis.
   - **DIVIDEND_REINVEST**: Real cost basis purchase lot with acquisition date equal to reinvestment date.

4. **FIFO Matching Engine (`FifoMatcher`)**:
   - Matches `DISPOSAL` events against open lots chronologically.
   - Computes realized capital gain per matched slice.

5. **Tax Classifier & Exemption Tracker (`TaxClassifier`, `ExemptionTracker`)**:
   - Loads versioned rule configuration (`FY2026-27.yaml`).
   - Evaluates holding period thresholds (12 months vs 24 months vs never LT for Sec 50AA debt funds).
   - Tracks ₹1.25L annual LTCG exemption limit per FY under Section 112A for equity / equity MFs.
   - Evaluates grandfathering FMV as of 31-Jan-2018 ($\text{cost} = \max(\text{actual cost}, \text{FMV}_{2018-01-31})$).

---

### 2.2 Package: `tax-adapter-persistence`

* **Directory Location**: `tax-domain/tax-adapter-persistence`
* **Technology**: Kotlin, PostgreSQL, HikariCP, Exposed / JDBC raw SQL, Flyway migrations.

#### **Goals & Responsibilities**:
1. Maintain immutable append-only `tax_events` table indexed by `asset_id`, `event_date`, and `source_document_id`.
2. Materialize and update `open_lots` projection for quick FIFO lookup and cockpit valuation queries.
3. Support full event replay to rebuild projections if tax rules or retroactive classifications change.

---

### 2.3 Package: `tax-adapter-api`

* **Directory Location**: `tax-domain/tax-adapter-api`
* **Technology**: Kotlin 2.x, Ktor Server (Netty/CIO engine), Jackson JSON serializer, ContentNegotiation.

#### **Goals & Responsibilities**:
1. Expose REST endpoints:
   - `POST /api/v1/statements/upload`: Accepts statement files/events payload from python `parsers`.
   - `GET /api/v1/tax/reports/itr2?fy=2026-27`: Computes and exports FY realized gains report matching ITR-2 Schedule CG.
   - `GET /api/v1/tax/exemption-status?fy=2026-27`: Returns current ₹1.25L exemption usage.
2. Load versioned tax rule files from `/rules/` directory.

---

### 2.4 Package: `parsers`

* **Directory Location**: `parsers`
* **Technology**: Python 3.11+, `pdfplumber`, `pypdf`, `pandas`, `pydantic`.

#### **Goals & Responsibilities**:
1. **`CasPdfParser`**: Parses CAMS & KFintech CAS PDF statements (including password-protected PDFs). Extracts scheme names, ISINs, transaction dates, transaction types, units, prices, gross amounts, and reported closing balances.
2. **`BrokerCsvParser`**: Parses trade/tax P&L CSV files from brokers (Zerodha, Groww, ICICI Direct, LRS brokers).
3. **Pre-flight Reconciliation**: Sums signed deltas locally to ensure statement integrity before POSTing normalized `TaxEvent` JSON payloads to `tax-adapter-api`.

---

### 2.5 Package: `valuation-engine`

* **Directory Location**: `valuation-engine`
* **Technology**: Kotlin 2.x / Ktor Client.

#### **Goals & Responsibilities**:
1. **AMFI EOD NAV Sync**: Daily sync of EOD NAVs from official AMFI text/API feeds (`https://www.amfiindia.com/net-asset-value/nav-history`).
2. **Stock & ETF Price Sync**: External market feed adapter for exchange-traded assets, designed to degrade gracefully (using last-known stale NAV with visual indicator) if feeds fail.
3. **XIRR Calculation Engine**: Accurate cash-flow timing internal rate of return calculator across portfolio and per-asset levels.
4. **Decision Radar & Harvest Advisor**:
   - Maturation ladder: identifies open lots approaching 12/24 month LTCG thresholds.
   - Loss harvester: flags unrealized loss lots eligible to offset realized STCG/LTCG gains before fiscal year end.

---

### 2.6 Package: `web-cockpit`

* **Directory Location**: `web-cockpit`
* **Technology**: HTML/JS/CSS (or SvelteKit/HTMX dense dark mode PWA).

#### **Goals & Responsibilities**:
1. Provide responsive, keyboard-navigable dark-mode UI cockpit.
2. Surface key metrics: Net Worth, Realized & Unrealized Gains, LTCG Exemption Progress Meter (₹1.25L), Asset Allocation Matrix, and Decision Radar alerts.
3. Completely read-only with respect to the tax ledger—never submits mutating tax classification changes directly from valuation dashboard.

---

## 3. Rules Engine Reference (`FY2026-27.yaml`)

```yaml
fy: "2026-27"
effective_start: "2026-04-01"
effective_end: "2027-03-31"

rules:
  equity_listed:
    ltcg_threshold_months: 12
    ltcg_rate: 0.125
    stcg_rate: 0.20
    annual_exemption: 125000
    section: "112A / 111A"

  specified_debt_fund:
    effective_from: "2023-04-01"
    always_short_term: true
    debt_pct_threshold: 0.65
    taxation_mode: "SLAB_RATE"
    section: "50AA"

  gold_silver_international:
    ltcg_threshold_months: 24
    ltcg_rate: 0.125
    stcg_rate: "SLAB_RATE"
    annual_exemption: 0

  sgb:
    maturity_years: 8
    maturity_gain_exempt: true
    exchange_sale_stcg_rate: "SLAB_RATE"
    exchange_sale_ltcg_rate: 0.125
    exchange_sale_ltcg_threshold_months: 24

  grandfathering:
    cutoff_date: "2018-01-31"
```

---

## 4. Implementation Phasing Matrix

| Phase | Package Focus | Key Deliverable |
|---|---|---|
| **Phase 1** | `tax-core` | Complete domain model, FIFO matcher, tax classifier, signed unit-delta reconciliation gate, unit tests against hand-crafted fixtures. |
| **Phase 2** | `parsers` & `tax-adapter-api` | Python CAMS/KFintech CAS parser, broker CSV parsers, Ktor upload route, and Postgres `tax_events` persistence. |
| **Phase 3** | `tax-adapter-persistence` & Reporting | Materialized projections replay, ITR-2 Schedule CG report exporter, ₹1.25L exemption tracker. |
| **Phase 4** | `valuation-engine` & `web-cockpit` | AMFI NAV fetcher, XIRR engine, harvest advisor, Decision Radar dark-mode cockpit UI. |
