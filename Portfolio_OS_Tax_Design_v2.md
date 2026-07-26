# System Design Document: Portfolio OS - Tax Reporting & Export Schema Upgrade (v2.0)

## 1. Executive Summary
This document details the architectural expansion of the Portfolio OS microservices environment to support automated Indian Income Tax (ITR-2) reporting. By standardizing data exports to match the Income Tax offline utility, this upgrade eliminates manual data entry. The system handles New Tax Regime calculations, bypassing legacy (pre-2018) equity grandfathering, and introduces specific parsers for Section 50AA (Debt) and Schedule FA (Foreign Assets).

## 2. Database Architecture Modifications (PostgreSQL)
To support exact ITR-2 CSV requirements, the core transaction ledger requires schema extensions.

### 2.1 Table: `asset_metadata`
Categorization is strictly enforced to trigger the correct tax calculation strategy.
*   `asset_type`: Enum (`DOMESTIC_EQUITY`, `DEBT_MF_50AA`, `NON_EQUITY_MF_INTL`, `DIRECT_FOREIGN_EQUITY`)
*   `isin_code`: VARCHAR(12) (Required for `DOMESTIC_EQUITY` / Schedule 112A)
*   `foreign_entity_address`: TEXT (Required for Schedule FA)
*   `country_code`: VARCHAR(2) (e.g., 'US', required for Schedule FA)

### 2.2 Table: `tax_reporting_snapshots`
Required for tracking non-transactional compliance data, particularly for foreign assets.
*   `financial_year`: VARCHAR(9) (e.g., "2025-2026")
*   `asset_id`: UUID (Foreign Key)
*   `peak_value_inr`: NUMERIC (Highest intra-year value, required for Schedule FA)
*   `closing_balance_inr`: NUMERIC (End of foreign accounting period - Dec 31st)

## 3. Backend Services (Spring Boot)
The quantitative analytics engine is expanded with a dedicated `tax-export-service`.

### 3.1 ITR Data Aggregation Logic
*   **Schedule 112A Aggregator (Equity):** Groups transactions by `isin_code`. Hardcodes `fmv_jan_2018` to `0`. Calculates total units, full value of consideration, and cost of acquisition using FIFO (First-In-First-Out) matching.
*   **Section 50AA Processor (Debt):** Flags all debt fund redemptions post-April 2023 as STCG. Bypasses 36-month checks and routes directly to the Schedule CG aggregator for slab-rate taxation.
*   **Schedule FA Engine (Foreign):** Implements a scheduled job fetching the SBI Telegraphic Transfer (TT) buying rate as of the last day of the preceding accounting year. Converts USD asset values (initial investment, peak value, gross proceeds) into INR.

### 3.2 API Contract
*   **Endpoint:** `GET /api/v1/tax/export/itr2?fy=2025-2026`
*   **Payload:** Returns a compressed `.zip` containing accurately mapped `.csv` files:
    *   `schedule_112a.csv`
    *   `schedule_cg_stcg.csv`
    *   `schedule_fa.csv`

## 4. Frontend Implementation (React)
Integration of a dedicated "Tax Operations" layout.

### 4.1 Tax Season Dashboard
*   **Real-time Liability Visualizer:** A component rendering the current FY's realized STCG and LTCG liabilities.
*   **Schedule FA Validator:** A pre-flight checklist UI ensuring all direct foreign assets have a recorded peak intra-year value before export generation is permitted.

## 5. Quality of Life (QOL) & Visual Enhancements

### 5.1 Visual & Thematic Upgrades
*   **Aesthetic Profile:** Implement a high-contrast, uncluttered styling architecture inspired by Noctalia themes. Utilize deep, pure black backgrounds with sharply defined, desaturated accent colors (e.g., muted cyan or soft magenta) to prioritize data readability over decorative UI elements.
*   **Tiling-Friendly Layouts:** Eliminate floating modals, overlapping dialogue boxes, and fixed-width canvas components. Ensure the React UI relies strictly on CSS Grid/Flexbox behaviors to integrate perfectly into tiling window manager workflows without visual clipping or overflow issues.

### 5.2 Tax-Aware Rebalancing Math
*   **Predictive Tax Impact:** Upgrade the factor-based portfolio rebalancing engine to calculate the *tax drag* of a proposed rebalance.
*   **LTCG Constraint Toggle:** Introduce a QOL setting that constrains the quantitative algorithm to prioritize selling units that cross the 12-month threshold, automatically attempting to harvest the ₹1.25L tax-free LTCG allowance to improve overall capital efficiency.

## 6. Containerization & Deployment
*   Deploy the `tax-export-service` as an independent Docker container.
*   Map local volume mounts for the CSV export directory to allow seamless access from the host OS.
*   Ensure the tax engine's memory limits in `docker-compose.yml` are strictly defined, preventing resource contention with other locally hosted, compute-heavy containers (such as LLM instances) running concurrently on the host hardware.
