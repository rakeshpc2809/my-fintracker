# High-Level Design v2: Personal Capital Gains Tax Ledger & Portfolio Cockpit

**Status:** Consolidated for implementation
**Owner:** Single-user, self-hosted
**Target:** CachyOS, Docker Compose, Tailscale private network

---

## 1. Purpose and Scope

Two bounded contexts, sharing one event log:

1. **Tax Domain (primary, high-reliability).** Deterministic, auditable STCG/LTCG classification from raw transaction events, producing FY-wise reports shaped for ITR-2 filing.
2. **Valuation Domain (secondary, additive).** Live NAV/price sync, XIRR computation, asset allocation view, and a decision-radar dashboard — consumes the same event log but never feeds back into tax classification.

The separation matters: the tax domain must be correct even if the valuation domain's external data feeds are down, stale, or wrong. A flaky NAV API should degrade the dashboard, never a filing number.

**Out of scope (v1):** F&O/business income, property, multi-user, automated trade execution.
**Non-goal:** this is not a substitute for CA review before filing.

---

## 2. Design Rationale

**Event sourcing.** CAS/broker statements are inherently event logs (acquisitions, SIPs, splits, bonuses, dividends, redemptions). Storing only current-state balances discards what FIFO matching and auditability need. Tax rules also change almost every budget cycle (Section 50AA's debt-fund definition moved twice in three years) — an append-only raw ledger lets you replay history under a corrected rule set without touching source records.

**Hexagonal architecture.** Domain logic (FIFO matching, classification, gains calculation) is pure and framework-free, so it's unit-testable against fixtures with zero I/O, and parsers/storage/UI can change independently behind ports.

**Ktor over Spring Boot.** Ktor is a thin HTTP/routing layer with no reflection-based DI bleeding into the domain core, low resource footprint, and native coroutine support for async NAV syncs and projection rebuilds — a reasonable fit for a single-user containerized service. (Treat specific memory/startup numbers as indicative, not guaranteed — verify on your own hardware rather than taking any framework's benchmark claims at face value.)

---

## 3. Functional Requirements

| ID | Domain | Requirement |
|---|---|---|
| F1 | Tax | Parse CAS (CAMS/KFintech) PDFs into structured transaction events |
| F2 | Tax | Parse broker tax P&L CSVs (direct equity, LRS foreign holdings) |
| F3 | Tax | **Reconciliation gate:** validate parsed statement against its own declared closing balance before ledger commit; block and flag on mismatch |
| F4 | Tax | Maintain immutable, append-only event ledger, tagged to source document |
| F5 | Tax | FIFO lot matching per asset/ISIN on disposal events |
| F6 | Tax | Classify STCG/LTCG via versioned, FY-specific rule config |
| F7 | Tax | Handle special cases: Section 50AA, SGB maturity exemption vs. exchange-sale, pre-2018 grandfathering, **bonus/split cost-basis reallocation** |
| F8 | Tax | Track running ₹1.25L equity LTCG exemption usage per FY |
| F9 | Tax | Export FY report matching ITR-2 capital gains schedule structure |
| F10 | Tax | Flag LRS foreign holdings for Schedule FA disclosure awareness |
| F11 | Valuation | Sync EOD NAVs (AMFI daily feed for MFs) and prices (exchange/vendor feed for stocks/ETFs) |
| F12 | Valuation | Compute portfolio and per-asset XIRR from actual cash-flow timing |
| F13 | Valuation | Surface days-to-LTCG maturation ladder for open lots |
| F14 | Valuation | Surface tax-loss harvesting candidates (open lots at unrealized loss) |
| F15 | Valuation | Cockpit dashboard: net worth, unrealized gain, exemption usage, allocation matrix |

F11–F15 depend only on read access to the tax domain's event log and open-lot projection — they never write to it.

---

## 4. Tax Rules Matrix (FY 2026-27 reference)

| Asset class | LT threshold | LTCG | STCG | Notes |
|---|---|---|---|---|
| Listed equity / equity MF (≥65% equity) | 12 months | 12.5% above ₹1.25L/FY | 20% (Sec 111A) | Exemption applies only here |
| Specified debt fund (≥65% debt, acquired ≥1 Apr 2023) | never LT | — | slab rate (Sec 50AA) | Pre-Apr-2023 units keep old 24-month LTCG treatment |
| Gold/silver ETF, international fund | 24 months | 12.5%, no exemption | slab rate | Confirm per-fund structure at implementation time |
| SGB | held to maturity (8 yr) | exempt | slab rate (interest only) | Exchange sale before maturity is taxable |
| Pre-31-Jan-2018 equity | 12 months | 12.5%, grandfathered cost basis | 20% | cost = max(actual cost, 31-Jan-2018 FMV) |
| Foreign equity/ETF (LRS route) | 24 months | 12.5% | slab rate | Also triggers Schedule FA |

---

## 5. System Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                    Tax Domain Core (pure Kotlin)                    │
│  TaxEvent · Lot · FifoMatcher · TaxClassifier · GainsCalculator     │
│  ExemptionTracker · RuleSet(YAML)                                    │
│  — zero I/O, zero framework imports, fully unit-testable —          │
└───────────────────────────────▲──────────────────────┬──────────────┘
                                 │ ports                │ ports
┌────────────────────────────────┴───────┐   ┌──────────▼───────────────┐
│ Inbound adapters                        │   │ Outbound adapters         │
│  CasStatementParser (Python/pdfplumber) │   │ EventStore (Postgres)     │
│  BrokerCsvParser                        │   │ RuleSetProvider (YAML)    │
│  Ktor HTTP controllers                  │   │ ReportExporter (ITR)      │
└──────────────────────────────────────────┘   └───────────────────────────┘
                                 │ (read-only)
                                 ▼
┌───────────────────────────────────────────────────────────────────┐
│              Valuation Domain (separate module, additive)           │
│  NavSyncAdapter (AMFI/exchange feed) · XirrEngine · HarvestAdvisor  │
│  Cockpit projections — reads tax event log + open lots, never writes │
└───────────────────────────────▲──────────────────────┬──────────────┘
                                 │                       │
                          SvelteKit / HTMX cockpit UI (dark mode, PWA)
```

---

## 6. Domain Model

### 6.1 Event schema (write-side, append-only)

```kotlin
data class TaxEvent(
    val id: UUID,
    val assetId: String,
    val eventType: EventType,
    val eventDate: LocalDate,
    val units: BigDecimal,
    val pricePerUnit: BigDecimal,
    val grossAmount: BigDecimal,
    val sourceDocumentId: UUID,
    val ingestedAt: Instant
)

enum class EventType {
    ACQUISITION, SIP_INSTALMENT, DISPOSAL,
    BONUS, SPLIT, DIVIDEND_REINVEST,
    SGB_INTEREST, SGB_MATURITY, MERGER
}
```

### 6.2 Signed unit-delta model (fixes the reconciliation bug)

Rather than hardcoding "closing units = acquisitions − disposals" — which silently breaks the moment a bonus, split, or reinvested dividend is involved — each event type declares its own unit effect, and reconciliation sums over *all* events for the asset:

```kotlin
fun unitDelta(event: TaxEvent): BigDecimal = when (event.eventType) {
    EventType.DISPOSAL -> -event.units
    EventType.SGB_INTEREST -> BigDecimal.ZERO   // cash event, no units
    else -> event.units   // ACQUISITION, SIP_INSTALMENT, BONUS, SPLIT,
                          // DIVIDEND_REINVEST, SGB_MATURITY, MERGER-in
}

// Reconciliation gate:
// sum(unitDelta(e) for e in events[assetId]) == statement.declaredClosingUnits
// mismatch -> block ingestion, flag source document for manual review
```

This is extensible by construction: a new event type added later defaults to "adds units" unless explicitly marked otherwise, instead of requiring every reconciliation call site to be updated.

### 6.3 Cost-basis handling for non-cash events (new — Gemini's version didn't address this)

- **BONUS**: zero-cost lot, `grossAmount = 0`. Holding period starts from the bonus allotment date, not the original lot's date — a bonus unit is not automatically long-term just because the parent holding is.
- **SPLIT**: re-denominates an *existing* lot (same total cost, more units, same original acquisition date and holding-period clock) — must not create a new-cost lot.
- **DIVIDEND_REINVEST**: a genuine new lot with its own acquisition date and real cost basis, treated identically to a fresh purchase.

Getting this wrong is exactly the kind of error that stays invisible until the FIFO match on a years-later disposal produces a subtly wrong gain — worth encoding as explicit test fixtures per event type before touching a real parser.

### 6.4 Rule configuration (versioned per FY)

```yaml
# rules/FY2026-27.yaml
equity_listed:
  ltcg_threshold_months: 12
  ltcg_rate: 0.125
  stcg_rate: 0.20
  annual_exemption: 125000
specified_debt_fund:
  effective_from: 2023-04-01
  always_short_term: true
  debt_pct_threshold: 0.65
gold_silver_international:
  ltcg_threshold_months: 24
  ltcg_rate: 0.125
  annual_exemption: 0
sgb:
  maturity_years: 8
  maturity_gain_exempt: true
grandfather_date: 2018-01-31
```

---

## 7. Valuation Domain (Portfolio Cockpit)

Separate module, read-only against the tax event log plus an external NAV/price feed.

```
┌─────────────────────────────────────────────────────────────────────┐
│ PORTFOLIO COCKPIT                                    FY 2026-27       │
├─────────────────────────────────────────────────────────────────────┤
│ NET WORTH        UNREALIZED GAIN     LTCG EXEMPTION USED   XIRR       │
│ ₹ XX,XX,XXX      +₹ X,XX,XXX (+XX%)  [███████░░░] ₹Xk/1.25L  XX.X%    │
├─────────────────────────────────────────────────────────────────────┤
│ DECISION RADAR                                                        │
│  • N units of <fund> cross into LTCG in <X> days                      │
│  • Harvest ₹<X> unrealized loss in <fund> to offset realized STCG      │
├─────────────────────────────────────────────────────────────────────┤
│ ASSET ALLOCATION & RETURN MATRIX                                       │
│  Asset class     Allocation   Invested    Current    XIRR             │
│  Equity          XX%          ₹X          ₹X         XX%              │
│  Debt            XX%          ₹X          ₹X         XX%              │
│  Gold/Silver     XX%          ₹X          ₹X         XX%              │
│  Foreign (LRS)   XX%          ₹X          ₹X         XX%              │
└─────────────────────────────────────────────────────────────────────┘
```

- **NAV sync**: AMFI publishes a daily NAV file for all mutual funds — stable, free, no auth needed. For stocks/ETFs, exchange or vendor feeds tend to be less stable (unofficial APIs break without notice); treat this adapter as the one most likely to need maintenance, and design it to fail gracefully (stale-but-labeled NAV) rather than block the cockpit.
- **XIRR**: standard cash-flow-based IRR over each lot's acquisition/disposal events — independent of the tax classifier, computed purely for performance visibility.
- **Harvest advisor**: reads open lots at unrealized loss + realized gains for the FY, surfaces candidates — does not execute anything.

---

## 8. Technology Stack

| Layer | Choice | Notes |
|---|---|---|
| Domain + backend | Kotlin 2.x on Ktor | Pure domain core, thin HTTP layer |
| Parsing | Python (pdfplumber/pypdf) | Isolated adapter, swappable independently of domain logic |
| Storage | PostgreSQL | Single instance; event table + materialized projections, no dedicated event-sourcing framework needed at this scale |
| Frontend | SvelteKit or HTMX + Tailwind | Dense, dark-mode, keyboard-navigable; PWA manifest for Pixel 7a |
| Deployment | Docker Compose on CachyOS, bound to 127.0.0.1 | No public ports |
| Remote access | Tailscale | Private tailnet only |

---

## 9. Build Roadmap

| Phase | Scope | Definition of done |
|---|---|---|
| **1. Domain core** | `TaxEvent`, `Lot`, `FifoMatcher`, `TaxClassifier`, rule-config loader | Unit tests pass against hand-worked fixtures for every event type in 6.3, including bonus/split cost-basis cases, with zero I/O |
| **2. Ingestion & reconciliation** | CAS parser, broker CSV parser, reconciliation gate | A real historical CAS parses to a ledger whose signed-delta sum matches its declared closing balance; a deliberately corrupted statement is rejected |
| **3. Tax reporting** | Realized gains report, exemption tracker, ITR-shaped export | Output for a past FY matches the actual filed numbers for that year |
| **4. Valuation & cockpit** | NAV sync, XIRR engine, dashboard UI | Cockpit renders correctly even with NAV feed disabled/stale (degrades, doesn't break) |

Tax reporting (phase 3) ships before the cockpit (phase 4) deliberately — it's the higher-stakes, harder-to-get-wrong deliverable, and the cockpit is additive polish on top of a ledger that's already correct.

---

## 10. Risks and Open Questions

- Exchange/vendor NAV-and-price feeds for stocks/ETFs are the least stable dependency in the system — isolate behind a port so a broken feed only degrades the cockpit, never the tax domain.
- Debt-fund and international-fund classification rules have shifted twice in three budget cycles; each new budget requires a prompt new `FYxxxx-xx.yaml`, not a code change — but someone still has to notice the rule changed.
- Bonus/split/SGB cost-basis fixtures should be validated against real historical transactions before trusting the classifier on those cases.
- Whether surcharge/cess (income-dependent) belongs in this system or stays a CA/ITR-software concern is still open — current design produces gains figures only.

---

## 11. Disclaimer

Computes mechanically correct gains under explicitly encoded rules; it is not a substitute for CA review before filing, particularly around Section 50AA, grandfathering, and SGB edge cases that have shifted across recent budgets.
