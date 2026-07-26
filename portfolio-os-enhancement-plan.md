# Portfolio OS — Enhancement Plan (Core Ledger + Mobile Companion)

**Scope:** `tax-domain`, `valuation-engine`, `parsers`, `web-cockpit`, `mobile-app`
**Status:** Planning document — not yet actioned
**Read alongside:** `tax-ledger-hld-v2.md`, `docs/detailed_design.md` (authoritative design). `Portfolio_OS_Tax_Design_v2.md` describes a superseded Spring Boot/React plan — see Priority 3 for the one idea worth salvaging from it before archiving it.

---

## Guiding principle: two kinds of freshness

The mobile app's job is to be a fast daily dashboard, not a second compute engine for anything tax-sensitive. But "don't compute on the phone" was too blunt a rule — it collapses two different things that age at different rates:

| | Changes when | Where it should be computed |
|---|---|---|
| **Ledger state** (which lots exist, units, cost basis, category) | You transact — monthly SIPs, occasional trigger buys | Desktop only. Synced to phone, not recomputed there. |
| **Valuation** (what those lots are worth *today*) | Daily, via AMFI NAV — independent of whether the laptop is on | Phone, live, every time the dashboard opens |

Rule of thumb going forward: **anything that's pure arithmetic against already-synced facts is safe to compute on-device. Anything that's tax/business logic — FIFO construction, category classification, exemption tracking, rebalance tax-drag — stays server-side, single implementation.**

This principle should guide every mobile change below, and should stop any future temptation to duplicate more logic client-side "for freshness."

---

## Priority 0 — Backend correctness (do before trusting any output, mobile or web)

These affect both surfaces and are silent-wrong-number risks, not cosmetic issues.

1. **Wire the reconciliation gate into the actual upload path.**
   `ReconciliationGate.kt` and `reconciler.py` both work but neither is called from `StatementRoutes.kt`'s `/api/v1/statements/upload` handler, and `--closing-units` is never passed to the parser CLI. This is the one guarantee the whole redesign was built around (HLD §2, F3) — currently a no-op.
   - *Acceptance:* uploading a statement with a deliberately wrong declared closing balance is rejected with a clear error, not silently ingested.

2. **Load tax rules from `rules/FY2026-27.yaml` instead of hardcoding them.**
   `TaxClassifier` hardcodes 365/730-day thresholds, `ExemptionTracker` hardcodes ₹1.25L, `RebalanceEngine` hardcodes 12.5%/20%. None of them read the YAML that already exists for exactly this purpose.
   - *Acceptance:* changing a rate/threshold in the YAML changes computed output without a Kotlin code change or redeploy.

3. **Fix `RebalanceEngine`'s category-blind LTCG check.**
   It uses a blanket "≥365 days = LTCG" regardless of asset category, while `TaxClassifier` elsewhere correctly uses per-category thresholds (365 / 730 / never-LT). A debt fund held >1 year currently shows as LTCG-eligible in the rebalance preview when it should be always-short-term (Sec 50AA).
   - *Acceptance:* rebalance preview uses `TaxClassifier.classifyTaxTerm()` (or the YAML-driven equivalent from item 2), not its own day-count logic.

4. **Fix or explicitly flag grandfathering FMV, currently hardcoded to ₹0.**
   `Itr2CsvExporter.generateSchedule112aCsv()` never applies `cost = max(actual cost, FMV as of 31-Jan-2018)`. Understates cost basis → overstates LTCG for any pre-2018 holding.
   - *Acceptance:* either implement real FMV lookup, or block/label the export as unreliable for pre-2018 lots until implemented.

5. **Fix or remove the fabricated Schedule FA peak value.**
   `generateScheduleFaCsv()` computes peak value as `initialCost × 1.15` — not tracked, invented. This is the highest-risk single line in the codebase since Schedule FA carries disclosure obligations.
   - *Acceptance:* either implement real peak-value tracking (see Priority 3, item 2 for the data model to reuse) or remove the column / clearly mark it "estimate, verify manually" in the export.

6. **Remove or wire `HarvestAdvisor.kt`.**
   It's a dead stub (`return emptyList()`); the real logic is duplicated inline in `ReportRoutes.kt`. Pick one; delete the other.

7. **Cache the AMFI NAV fetch server-side.**
   Every dashboard load fires ~7 concurrent endpoint calls, each independently re-downloading and re-parsing the full AMFI feed. Add a short-TTL (e.g. 6–12 hour) in-process cache in `AmfiNavSync`. Not a correctness bug, but cheap to fix and currently wasteful on every page load.

---

## Priority 1 — Mobile app fixes

8. **Fix `recalculateLiveNavs()` in `+page.svelte`.**
   Current code approximates value using only `lots[0].costPerUnit` as a stand-in for the whole holding — wrong for any SIP-built position with multiple lots at different costs. Replace with: for each holding, sum `remainingUnits × currentNav` across *all* its lots (data already present in the synced snapshot).

9. **Add client-side holding-period / LTCG recompute using device "today."**
   Per the freshness principle above: `daysToLtcg` and `isLtcg` in the cached snapshot are only as fresh as the last sync. Recompute them on-device from the cached `acquisitionDate` + `category` against the phone's current date, same way `recalculateLiveNavs()` already refreshes value regardless of sync recency.

10. **Show two freshness timestamps, not one blended status string.**
    Replace the single `syncStatus` label with something like: *"Holdings as of 14 Jul (last desktop sync) · Valuation as of 25 Jul (live)."* Currently a 3-week-stale ledger and a same-day NAV both just say "Synced via Desktop P2P," which hides exactly the information a daily-glance dashboard should surface.

11. **Surface `navStale` in the mobile UI.**
    The server's `HoldingDetailDto.navStale` field is dropped by the mobile `Holding` TypeScript interface entirely. Add it back and show a visual indicator per-holding when NAV data is missing/stale (falls back to cost basis).

12. **Add a settings screen.**
    Replace the hardcoded `desktopIp` with an editable field. Prefer a stable Tailscale MagicDNS hostname (e.g. `cachyos.tailXXXX.ts.net`) over a raw LAN IP so it survives DHCP/IP churn without an APK rebuild.

---

## Priority 2 — Mobile feature additions (make it a real "dashboard," not just a net-worth ticker)

The mobile app currently shows only net worth, gain, and holdings/lots. It surfaces none of the tax-specific content that's the actual point of the rest of the system, even though the server already computes it.

13. **LTCG exemption meter** (compact version of the web cockpit's progress bar).
14. **Decision Radar** — top 2–3 harvest/maturation alerts, same data the web cockpit renders.
15. **XIRR** — already computed server-side (`/portfolio/summary`), just not displayed on mobile.
16. **Local push notifications** (Capacitor Local Notifications) for maturation-ladder and harvest-opportunity alerts — leans into the phone's always-on advantage over the desktop instead of requiring the app to be opened to discover anything.
17. **New lean server endpoint, `/api/v1/mobile/summary`.**
    A smaller payload purpose-built for the daily glance: net worth, gain, top radar items, exemption-meter state. Keep the existing full snapshot endpoint for when the person taps into drill-down detail. Forces an explicit decision about what the dashboard prioritizes, rather than rendering the entire data model on a phone screen.

---

## Priority 3 — Architectural cleanup

18. **Archive `Portfolio_OS_Tax_Design_v2.md`, but port one idea from it first.**
    Its `asset_metadata` table (ISIN → explicit `asset_type` enum, set once per fund) is meaningfully more reliable than `TaxClassifier.detectCategory()`'s current substring-matching on fund names (`"BOND"`, `"GILT"`, `"DEBT"`). Any fund whose name doesn't contain one of these keywords silently falls through to `EQUITY` — worth replacing with an explicit per-ISIN mapping table, given misclassification here has real tax consequences.
    Its `tax_reporting_snapshots` table (with a real `peak_value_inr` column) is also the right data model for fixing item 5 above (Schedule FA).

19. **Verify the Tailscale/Docker network binding actually works as the mobile README assumes.**
    `docker-compose.yml` binds the API to `127.0.0.1:8080` only, but the mobile P2P sync depends on reaching a `100.x.y.z` Tailscale address. A loopback-only bind won't be reachable from the tailnet interface unless something (e.g. `tailscale serve`, a proxy) bridges it. Confirm on the actual CachyOS host before relying on it.

20. **Formalize the "server is the single implementation" rule for anything tax/business-logic.**
    FIFO construction, category classification, exemption tracking, and rebalance tax-drag math should have exactly one implementation (server-side). Document this explicitly (e.g. in `docs/detailed_design.md`) so future mobile features don't reintroduce a second compute path the way the current NAV-fetch duplication did.

---

## Suggested sequencing

1. **Priority 0** first — these are silent-wrong-number risks in a system meant to feed real tax filings. Nothing downstream is trustworthy until these land.
2. **Priority 1** next — mobile bug fixes are small, isolated, and unblock trusting the phone's numbers day-to-day.
3. **Priority 2** — feature work, once the foundation under it is solid.
4. **Priority 3** — cleanup/hardening, can run in parallel with the above since it's mostly deletion, verification, and documentation rather than new logic.
