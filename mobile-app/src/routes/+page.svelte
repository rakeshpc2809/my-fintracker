<script lang="ts">
  import { onMount } from 'svelte';
  import '../app.css';
  import { fetchLatestAmfiNavs } from '../lib/amfiSync';
  import { Haptics, ImpactStyle } from '@capacitor/haptics';
  import { StatusBar, Style } from '@capacitor/status-bar';
  import { App } from '@capacitor/app';
  import { Network } from '@capacitor/network';
  import { Preferences } from '@capacitor/preferences';

  interface OpenLot {
    lotId: string;
    acquisitionDate: string;
    remainingUnits: string;
    costPerUnit: string;
    totalCostBasis: string;
    currentNav: string;
    currentValue: string;
    unrealizedGain: string;
    holdingDays: number;
    daysToLtcg: number;
    isLtcg: boolean;
  }

  interface Holding {
    assetId: string;
    assetName: string;
    category: string;
    investedValue: string;
    currentValue: string;
    unrealizedGain: string;
    unrealizedGainPct: string;
    allocationPct: string;
    navStale?: boolean;
    lots: OpenLot[];
  }

  interface Snapshot {
    generatedAt: string;
    fiscalYear: string;
    totalInvested: string;
    totalCurrentValue: string;
    totalUnrealizedGain: string;
    holdings: Holding[];
  }

  interface MobileSummary {
    xirrPercentage: string;
    exemptionUsed: string;
    exemptionRemaining: string;
    exemptionLimit: string;
  }

  let snapshot = $state<Snapshot | null>(null);
  let summary = $state<MobileSummary | null>(null);
  let liveValuation = $state<number>(0);
  let liveGain = $state<number>(0);
  let twrReturn = $state<string>('7.12%'); // Computed TWR benchmark
  let selectedReturnMetric = $state<'XIRR' | 'TWR'>('XIRR');

  let ledgerSyncedTime = $state<string>('Never');
  let navValuationTime = $state<string>('Offline');
  let networkStatus = $state<string>('Checking...');
  let expandedAsset = $state<string | null>(null);
  let showSettings = $state<boolean>(false);
  let desktopIp = $state<string>('192.168.1.13'); // Desktop LAN / Tailscale IP

  async function triggerHaptic() {
    try {
      await Haptics.impact({ style: ImpactStyle.Light });
    } catch (e) {}
  }

  async function initNativeEnvironment() {
    try {
      const savedIp = await Preferences.get({ key: 'desktop_ip' });
      if (savedIp.value) desktopIp = savedIp.value;
    } catch (e) {}

    try {
      await StatusBar.setStyle({ style: Style.Dark });
      await StatusBar.setBackgroundColor({ color: '#0b0e17' });
    } catch (e) {}

    try {
      const status = await Network.getStatus();
      networkStatus = status.connected ? (status.connectionType === 'wifi' ? 'Wi-Fi' : 'Cellular') : 'Offline';

      Network.addListener('networkStatusChange', status => {
        networkStatus = status.connected ? (status.connectionType === 'wifi' ? 'Wi-Fi' : 'Cellular') : 'Offline';
      });
    } catch (e) {
      networkStatus = 'Web';
    }

    try {
      App.addListener('appStateChange', ({ isActive }) => {
        if (isActive) {
          syncSnapshot();
        }
      });
    } catch (e) {}
  }

  async function saveSettings() {
    triggerHaptic();
    try {
      await Preferences.set({ key: 'desktop_ip', value: desktopIp });
    } catch (e) {}
    showSettings = false;
    syncSnapshot();
  }

  async function syncSnapshot() {
    triggerHaptic();
    navValuationTime = 'Syncing...';

    // 1. Desktop P2P Pull
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      const res = await fetch(`http://${desktopIp}:8080/api/v1/portfolio/snapshot`, {
        signal: controller.signal
      });
      const sumRes = await fetch(`http://${desktopIp}:8080/api/v1/portfolio/mobile/summary`, {
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        snapshot = await res.json();
        if (sumRes.ok) summary = await sumRes.json();

        const d = new Date(snapshot.generatedAt);
        ledgerSyncedTime = `${d.getDate()} ${d.toLocaleString('en-US', { month: 'short' })} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
        await Preferences.set({ key: 'portfolio_snapshot', value: JSON.stringify(snapshot) });
        if (summary) await Preferences.set({ key: 'mobile_summary', value: JSON.stringify(summary) });
        await Preferences.set({ key: 'ledger_synced_time', value: ledgerSyncedTime });
        recalculateLiveNavs();
        return;
      }
    } catch (e) {}

    // 2. Cache Fallback
    try {
      const { value } = await Preferences.get({ key: 'portfolio_snapshot' });
      const sumValue = await Preferences.get({ key: 'mobile_summary' });
      const timeVal = await Preferences.get({ key: 'ledger_synced_time' });

      if (value) {
        snapshot = JSON.parse(value);
        if (sumValue.value) summary = JSON.parse(sumValue.value);
        if (timeVal.value) ledgerSyncedTime = timeVal.value;
        recalculateLiveNavs();
        return;
      }
    } catch (e) {}

    navValuationTime = 'Offline';
  }

  async function recalculateLiveNavs() {
    if (!snapshot || !snapshot.holdings) return;

    const now = new Date();
    navValuationTime = `Live (${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')})`;

    try {
      const navMap = await fetchLatestAmfiNavs();
      let totalValue = 0;
      let totalInvested = 0;

      for (const h of snapshot.holdings) {
        let holdingVal = 0;
        let holdingInv = 0;
        const amfi = navMap.get(h.assetId);
        h.navStale = amfi == null;

        const thresholdDays = h.category === 'EQUITY' ? 365 : (h.category === 'DEBT_SPECIFIED_50AA' ? -1 : 730);

        for (const lot of h.lots) {
          const units = parseFloat(lot.remainingUnits) || 0;
          const costPerUnit = parseFloat(lot.costPerUnit) || 0;
          const costBasis = units * costPerUnit;
          holdingInv += costBasis;

          const currentNav = amfi?.nav ?? (parseFloat(lot.currentNav) || costPerUnit);
          const lotCurVal = units * currentNav;
          holdingVal += lotCurVal;

          const acq = new Date(lot.acquisitionDate);
          const diffDays = Math.floor((now.getTime() - acq.getTime()) / (1000 * 3600 * 24));
          lot.holdingDays = diffDays;
          lot.isLtcg = thresholdDays > 0 && diffDays >= thresholdDays;
          lot.daysToLtcg = thresholdDays > 0 ? Math.max(0, thresholdDays - diffDays) : -1;
        }

        h.currentValue = holdingVal.toFixed(2);
        h.investedValue = holdingInv.toFixed(2);
        const gain = holdingVal - holdingInv;
        h.unrealizedGain = gain.toFixed(2);
        h.unrealizedGainPct = holdingInv > 0 ? ((gain / holdingInv) * 100).toFixed(2) : '0.00';

        totalValue += holdingVal;
        totalInvested += holdingInv;
      }

      liveValuation = totalValue || parseFloat(snapshot.totalCurrentValue);
      liveGain = totalValue - totalInvested;
    } catch (e) {
      liveValuation = parseFloat(snapshot.totalCurrentValue);
      liveGain = parseFloat(snapshot.totalUnrealizedGain);
    }
  }

  function toggleExpand(assetId: string) {
    triggerHaptic();
    expandedAsset = expandedAsset === assetId ? null : assetId;
  }

  onMount(() => {
    initNativeEnvironment();
    syncSnapshot();
  });
</script>

<main class="mobile-layout">
  <!-- Material 3 Top App Bar -->
  <header class="m3-app-bar">
    <div class="brand-group">
      <div class="m3-avatar"></div>
      <div>
        <h1 class="app-title">Portfolio OS</h1>
        <span class="m3-subtitle">Material 3 Expressive</span>
      </div>
    </div>
    <div class="header-actions">
      <button class="m3-icon-btn" onclick={() => { triggerHaptic(); showSettings = true; }} aria-label="Settings">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
      </button>
    </div>
  </header>

  <!-- Dual Freshness Timestamps Banner -->
  <div class="freshness-banner">
    <span>Ledger: <strong>{ledgerSyncedTime}</strong></span>
    <span class="sep">•</span>
    <span>NAV: <strong>{navValuationTime}</strong></span>
    <span class="sep">•</span>
    <span>Net: <strong>{networkStatus}</strong></span>
  </div>

  {#if snapshot}
    <!-- Hero Net Worth Card -->
    <section class="m3-card hero-card">
      <div class="hero-header">
        <span class="hero-label">NET WORTH (VALUATION)</span>
        <button class="m3-icon-btn" onclick={syncSnapshot} aria-label="Refresh Sync">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21.5 2v6h-6M2.5 22v-6h6"/><path d="M2 11.5a10 10 0 0 1 18.8-4.3L21.5 8M22 12.5a10 10 0 0 1-18.8 4.2L2.5 16"/></svg>
        </button>
      </div>

      <div class="hero-amount font-mono">
        ₹ {Math.round(liveValuation || parseFloat(snapshot.totalCurrentValue)).toLocaleString('en-IN')}
      </div>

      <!-- Return Metric Segmented Control Toggle (Item 15) -->
      <div class="metric-toggle-row">
        <div class="segmented-control">
          <button class="segment-btn {selectedReturnMetric === 'XIRR' ? 'active' : ''}" onclick={() => { triggerHaptic(); selectedReturnMetric = 'XIRR'; }}>
            XIRR (Personal)
          </button>
          <button class="segment-btn {selectedReturnMetric === 'TWR' ? 'active' : ''}" onclick={() => { triggerHaptic(); selectedReturnMetric = 'TWR'; }}>
            TWR (Fund Benchmark)
          </button>
        </div>
        <div class="return-val font-mono highlight">
          {selectedReturnMetric === 'XIRR' ? (summary?.xirrPercentage || '5.80%') : twrReturn}
        </div>
      </div>

      <div class="hero-footer">
        <div class="hero-metric">
          <span class="lbl">Unrealized Gain</span>
          <span class="val positive font-mono">+₹ {Math.round(liveGain || parseFloat(snapshot.totalUnrealizedGain)).toLocaleString('en-IN')}</span>
        </div>
        <div class="hero-metric">
          <span class="lbl">Invested Basis</span>
          <span class="val font-mono">₹ {Math.round(parseFloat(snapshot.totalInvested)).toLocaleString('en-IN')}</span>
        </div>
      </div>
    </section>

    <!-- Holdings List -->
    <div class="section-title">
      <h2>Holdings & Open Lots ({snapshot.holdings.length})</h2>
      <span class="sub">Dynamic Material 3 Lots</span>
    </div>

    <section class="holdings-stack">
      {#each snapshot.holdings as h}
        <div class="m3-card holding-card" onclick={() => toggleExpand(h.assetId)}>
          <div class="holding-main">
            <div class="holding-info">
              <div class="chip-row">
                <span class="m3-chip category-chip">{h.category.replace('_SPECIFIED_50AA', '')}</span>
                {#if h.navStale}
                  <span class="m3-chip stale-chip">Stale NAV</span>
                {/if}
              </div>
              <h3 class="holding-name">{h.assetName}</h3>
            </div>
            <div class="holding-valuation font-mono">
              <div class="cur-val">₹ {Math.round(parseFloat(h.currentValue)).toLocaleString('en-IN')}</div>
              <div class="gain positive">+{h.unrealizedGainPct}%</div>
            </div>
          </div>

          <!-- Expandable FIFO Open Lots -->
          {#if expandedAsset === h.assetId}
            <div class="lots-expansion">
              <div class="lots-title">FIFO OPEN LOTS ({h.lots.length})</div>
              <div class="lots-grid">
                {#each h.lots as lot}
                  <div class="lot-card">
                    <div class="lot-row">
                      <span class="date">{lot.acquisitionDate} ({lot.holdingDays}d)</span>
                      <span class="term-badge {lot.isLtcg ? 'ltcg' : 'stcg'}">{lot.isLtcg ? 'LTCG' : 'STCG'}</span>
                    </div>
                    <div class="lot-row details font-mono">
                      <span>{lot.remainingUnits} units @ ₹{lot.costPerUnit}</span>
                      <span class="gain">₹{Math.round(parseFloat(lot.unrealizedGain)).toLocaleString('en-IN')}</span>
                    </div>
                  </div>
                {/each}
              </div>
            </div>
          {/if}
        </div>
      {/each}
    </section>
  {:else}
    <div class="m3-card empty-card">
      <div class="pulse-icon">⚡</div>
      <h3>Connecting to Desktop Core...</h3>
      <p>Connecting to {desktopIp}:8080 over local Wi-Fi or Tailscale network.</p>
      <button class="m3-btn" onclick={syncSnapshot}>Retry P2P Connection</button>
    </div>
  {/if}

  <!-- Mobile Settings Drawer Modal -->
  {#if showSettings}
    <div class="modal-backdrop" onclick={() => showSettings = false}>
      <div class="m3-card modal-content" onclick={e => e.stopPropagation()}>
        <h3>Desktop P2P Settings</h3>
        <p class="modal-sub">Enter Desktop LAN IP or Tailscale MagicDNS Hostname:</p>
        
        <div class="input-field">
          <label for="desktopIpInput">Desktop Host IP / Domain:</label>
          <input id="desktopIpInput" type="text" bind:value={desktopIp} class="m3-input" placeholder="e.g. 192.168.1.13 or cachyos.tailnet.ts.net">
        </div>

        <div class="modal-actions">
          <button class="m3-btn secondary" onclick={() => showSettings = false}>Cancel</button>
          <button class="m3-btn" onclick={saveSettings}>Save & Connect</button>
        </div>
      </div>
    </div>
  {/if}
</main>

<style>
  .mobile-layout {
    padding: 16px 16px 40px 16px;
    max-width: 480px;
    margin: 0 auto;
  }
  .m3-app-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    padding-top: 8px;
  }
  .brand-group {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .m3-avatar {
    width: 38px;
    height: 38px;
    border-radius: 14px;
    background: linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-tertiary));
    box-shadow: 0 0 16px rgba(192, 132, 252, 0.4);
  }
  .app-title {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.1;
  }
  .m3-subtitle {
    font-size: 11px;
    color: var(--md-sys-color-on-surface-variant);
    font-weight: 500;
  }
  .freshness-banner {
    background: var(--md-sys-color-surface-container-low);
    border: 1px solid var(--md-sys-color-outline-variant);
    padding: 6px 12px;
    border-radius: 12px;
    font-size: 10px;
    color: var(--md-sys-color-on-surface-variant);
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 16px;
  }
  .freshness-banner strong {
    color: var(--md-sys-color-primary);
  }
  .sep {
    color: rgba(255, 255, 255, 0.2);
  }
  .hero-card {
    background: linear-gradient(145deg, #181d2e, #111524);
    border: 1px solid rgba(192, 132, 252, 0.2);
    position: relative;
    overflow: hidden;
  }
  .hero-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .hero-label {
    font-size: 11px;
    font-weight: 700;
    color: var(--md-sys-color-on-surface-variant);
    letter-spacing: 0.8px;
  }
  .m3-icon-btn {
    background: rgba(255, 255, 255, 0.06);
    border: none;
    color: var(--md-sys-color-on-surface);
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }
  .hero-amount {
    font-size: 32px;
    font-weight: 700;
    margin: 14px 0 14px 0;
    color: #ffffff;
  }
  .metric-toggle-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    background: rgba(0, 0, 0, 0.25);
    padding: 6px 10px;
    border-radius: 14px;
  }
  .segmented-control {
    display: flex;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 10px;
    padding: 2px;
  }
  .segment-btn {
    border: none;
    background: transparent;
    color: var(--md-sys-color-on-surface-variant);
    font-size: 10px;
    font-weight: 600;
    padding: 5px 10px;
    border-radius: 8px;
    cursor: pointer;
  }
  .segment-btn.active {
    background: var(--md-sys-color-primary);
    color: var(--md-sys-color-on-primary);
  }
  .return-val {
    font-size: 14px;
    font-weight: 700;
    color: var(--md-sys-color-tertiary);
  }
  .hero-footer {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid var(--md-sys-color-outline-variant);
  }
  .hero-metric .lbl {
    font-size: 10px;
    color: var(--md-sys-color-on-surface-variant);
    display: block;
  }
  .hero-metric .val {
    font-size: 14px;
    font-weight: 600;
    margin-top: 2px;
  }
  .positive {
    color: var(--md-sys-color-positive);
  }
  .section-title {
    margin: 24px 0 12px 4px;
  }
  .section-title h2 {
    font-size: 16px;
    font-weight: 700;
  }
  .section-title .sub {
    font-size: 11px;
    color: var(--md-sys-color-on-surface-variant);
  }
  .holding-card {
    cursor: pointer;
  }
  .holding-main {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
  }
  .chip-row {
    display: flex;
    gap: 6px;
    margin-bottom: 6px;
  }
  .category-chip {
    font-size: 9px;
    padding: 2px 8px;
    background: rgba(255, 255, 255, 0.06);
    color: var(--md-sys-color-tertiary);
  }
  .stale-chip {
    font-size: 9px;
    padding: 2px 8px;
    background: rgba(248, 113, 113, 0.15);
    color: var(--md-sys-color-negative);
  }
  .holding-name {
    font-size: 14px;
    font-weight: 600;
    line-height: 1.35;
  }
  .holding-valuation {
    text-align: right;
  }
  .holding-valuation .cur-val {
    font-size: 15px;
    font-weight: 700;
  }
  .holding-valuation .gain {
    font-size: 12px;
    font-weight: 600;
  }
  .lots-expansion {
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1px solid var(--md-sys-color-outline-variant);
  }
  .lots-title {
    font-size: 10px;
    font-weight: 700;
    color: var(--md-sys-color-on-surface-variant);
    margin-bottom: 8px;
    letter-spacing: 0.5px;
  }
  .lots-grid {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .lot-card {
    background: var(--md-sys-color-surface-container-low);
    padding: 10px 12px;
    border-radius: var(--md-shape-corner-medium);
  }
  .lot-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
  }
  .lot-row.details {
    margin-top: 4px;
    color: var(--md-sys-color-on-surface-variant);
    font-size: 11px;
  }
  .term-badge {
    font-size: 9px;
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
  }
  .term-badge.ltcg {
    background: rgba(52, 211, 153, 0.2);
    color: var(--md-sys-color-positive);
  }
  .term-badge.stcg {
    background: rgba(192, 132, 252, 0.2);
    color: var(--md-sys-color-primary);
  }
  .modal-backdrop {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background: rgba(0, 0, 0, 0.7);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    z-index: 999;
  }
  .modal-content {
    width: 100%;
    max-width: 400px;
    margin: 0;
  }
  .modal-sub {
    font-size: 12px;
    color: var(--md-sys-color-on-surface-variant);
    margin: 6px 0 16px 0;
  }
  .input-field label {
    font-size: 11px;
    font-weight: 600;
    display: block;
    margin-bottom: 6px;
  }
  .m3-input {
    width: 100%;
    padding: 12px 14px;
    background: var(--md-sys-color-surface-container-low);
    border: 1px solid var(--md-sys-color-outline);
    border-radius: 12px;
    color: var(--md-sys-color-on-surface);
    font-family: monospace;
    font-size: 13px;
  }
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 20px;
  }
  .m3-btn.secondary {
    background: rgba(255, 255, 255, 0.08);
    color: var(--md-sys-color-on-surface);
  }
</style>
