# Portfolio Dashboard App — Feature Spec

Consolidated spec for additions to the current (dashboard) app: bucket-based rebalancing, tax efficiency, goal tracking, and FIRE tracking.

---

## 1. Bucket Structure

Funds are grouped into a small, flat set of buckets — no sub-buckets, no factor scoring layer (that belongs to the other app, not this one).

| Bucket | Contents |
|---|---|
| Equity Core | LargeMidcap 250, Value 30, Momentum Quality 50, Parag Parikh Flexi Cap |
| Equity Satellite | Small cap (microcap folded in / dropped per simplification decision) |
| Gold/Silver | SGBs, gold/silver ETFs (replacing legacy FoF) — sole non-domestic-equity hedge |
| Liquid/Buffer | Arbitrage fund (kept separate from the SA emergency-fund cash — different jobs, different target %) |

Each bucket gets one target % and one drift band. Both trigger types below operate at this bucket level, not per-fund — checking drift per-fund is noisy (one fund +2%, another −2%, net unchanged, false alerts fire).

---

## 2. Rebalancing Engine

Two independent trigger types feeding one shared recommendation output.

### 2a. Calendar trigger
- Fixed review dates: **March 15 / September 15** (aligned with Nifty LargeMidcap 250's own semi-annual reconstitution).
- `drift = current_% - target_%` per bucket.
- Only generate an action if `|drift| > band` (default band: 5 percentage points) — avoids churning small drift into taxable events for no real benefit.
- Output: buy/sell amounts per bucket to restore target.

### 2b. Market-drawdown trigger
- Input: benchmark index level (e.g., Nifty 500) + rolling all-time/52-week high.
- `drawdown = (rolling_high - current) / rolling_high`
- Ladder (default):
  - 10% down → deploy 25% of buffer
  - 15% down → deploy another 25% (50% cumulative)
  - 20%+ down → deploy remainder
- Track `rungs_fired` per drawdown cycle so the same rung doesn't refire; reset when index makes a new high.
- Runs on every data refresh, not just the calendar dates — drawdowns aren't calendar-bound.

Both triggers write to a shared `RebalanceRecommendation { fund, action: BUY/SELL, amount }` list — one recommendation view in the UI regardless of what fired it.

---

## 3. Tax Efficiency Layer

### 3a. Tax-lot tracking (prerequisite for everything below)
Each holding needs per-lot data, not just an aggregate:
```
TaxLot { fund_id, purchase_date, units, cost_basis }
```
India uses FIFO for MF/ETF redemptions — lots must be orderable and consumable in that order.

### 3b. LTCG-aware sell ordering
Whenever a rebalance recommends a SELL:
- Walk lots FIFO.
- Tag each consumed lot as LTCG (>12 months) or STCG (≤12 months).
- Surface the tax cost alongside the recommendation, e.g. "₹40K LTCG @ 12.5%, ₹10K STCG @ 20%" — lets you eyeball whether to execute now or wait for more lots to cross into LT.
- Note: arbitrage funds and equity funds share the 12-month LTCG threshold; international ETFs (if ever re-added) also use 12 months (post-FY2025-26 rule change) but STCG on those is slab-rate, not the flat 20% equity rate — worth flagging in the UI if that bucket ever returns.

### 3c. Annual exemption harvesting
The ₹1.25L LTCG exemption doesn't carry forward — unused headroom evaporates every March 31. Yearly job (run ~Feb, before FY close):
- Sum unrealized LTCG across all LT-eligible lots.
- If under ₹1.25L: no action.
- If over: recommend selling exactly enough LT-eligible units to realize ~₹1.25L in gains, then same-day rebuy of the same fund. No wash-sale rule in India for equity MFs — resets cost basis higher, tax-free, every year.
- Track `exemption_used_this_FY` so it doesn't double-count if other sells already used part of the ₹1.25L.

**Build order:** tax-lot tracking → calendar rebalance → drawdown trigger → harvesting job. Harvesting is the most valuable long-term but depends entirely on lot-level data existing first.

---

## 4. Goal Tags (purpose layer — independent of buckets)

Buckets answer "what should I hold." Goal tags answer "what is this money for." These are separate layers; a rupee's goal tag doesn't affect its bucket/risk treatment.

```
GoalTag = EMERGENCY | BIKE | WEDDING | RETIREMENT | UNALLOCATED
GoalAllocation { holding_id, goal_tag, amount }
```
Many-to-many: a single holding (e.g., bank balance) can be split across multiple goals.

Logic:
```
unallocated_cash = total_liquid_holdings - sum(goal_allocations where goal_tag != UNALLOCATED)
```
This — not the raw bank balance — is the number the dashboard should surface as "sitting idle."

---

## 5. FIRE Tracker Module

Separate module from the rebalance engine.

```
FireProfile {
  current_age (32),
  target_retirement_age (45),
  swr_percent (3.0-3.5, configurable — 40+ year decumulation horizon argues against the standard 4% rule),
  epf_balance,
  epf_unlock_age (58),
  real_return_rate (default 6%, configurable),
  scenarios: [FireScenario],
  next_review_date
}
FireScenario {
  label,                    // e.g. "With parents, no kid" / "Renting, with kid"
  monthly_expense_today,
  active: boolean           // which scenario drives the headline number
}
```

Logic:
- `annual_expense = monthly_expense_today * 12`
- `required_corpus = annual_expense / swr_percent`
- `investable_net_worth = total_net_worth - epf_balance - sum(goal_allocations where goal_tag in [BIKE, WEDDING, EMERGENCY])`
- `projected_corpus_at_target_age = investable_net_worth * (1 + real_return_rate)^years_remaining + future_value_of_monthly_contribution`
- `status = "on track" if projected_corpus_at_target_age >= required_corpus else "short by ₹X"`

### 5a. Scenario toggle
UI reads `scenarios[].active` to pick which scenario computes the headline status — same calculation path, just a different input. Cap at 2-4 saved scenarios to avoid noise.

### 5b. EPF segmentation
Two net-worth figures instead of one, both rendered on the overview:
- `total_net_worth` — everything.
- `fire_investable_net_worth` — excludes EPF (locked till 58-60) and non-retirement goal-tagged holdings. This is the figure that feeds the FIRE tracker, so EPF can't silently inflate an "on track" status that doesn't hold up between 45 and 58.

### 5c. Re-basing checkpoint
`next_review_date` set manually (~12 months out, around expected life-event timing). Dashboard shows a banner once passed: "Revisit your expense assumptions." No calculation — just a flag; scenario/expense numbers are updated manually when it fires.

---

## 6. Insurance Checklist

Not a calculation — a small persistent widget, unmissable near the top of the dashboard until resolved:
```
{ item: "Term Insurance", status: NOT_PURCHASED }
{ item: "Health Insurance", status: NOT_PURCHASED }
```
Context: current cover is employer-provided group term/health only, which lapses on the next job switch (expected within ~1 year). Collapses into a settings page once both flip to `PURCHASED`.

---

## Current Snapshot (for reference, as of this review)

- Net worth: ~₹24.31L (MF 70%, Liquid 20.5%, EPF 9.4%, Gold ~1%)
- MF holdings: 11 distinct funds, consolidating toward 8
- Active SIPs: ₹75,000/month across 6 funds
- Bank balance: ₹4.25L, earmarked (bike + personal wedding contribution + safety buffer) — not fully "idle" but not yet split by goal tag
- No personal term/health insurance (employer group cover only, lapses on next job switch)
- FIRE target: retire by 45, current age 32 — 13-year runway
