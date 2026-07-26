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

  let snapshot = $state<Snapshot | null>(null);
  let liveValuation = $state<number>(0);
  let syncStatus = $state<string>('Initializing...');
  let networkStatus = $state<string>('Checking network...');
  let expandedAsset = $state<string | null>(null);
  let desktopIp = $state<string>('192.168.1.13'); // Desktop LAN / Tailscale IP

  async function triggerHaptic() {
    try {
      await Haptics.impact({ style: ImpactStyle.Light });
    } catch (e) {
      // Browser fallback (ignored if unsupported)
    }
  }

  async function initNativeEnvironment() {
    try {
      await StatusBar.setStyle({ style: Style.Dark });
      await StatusBar.setBackgroundColor({ color: '#0b0e17' });
    } catch (e) {
      // Running in browser
    }

    try {
      const status = await Network.getStatus();
      networkStatus = status.connected ? (status.connectionType === 'wifi' ? 'Wi-Fi' : 'Cellular') : 'Offline';

      Network.addListener('networkStatusChange', status => {
        networkStatus = status.connected ? (status.connectionType === 'wifi' ? 'Wi-Fi' : 'Cellular') : 'Offline';
      });
    } catch (e) {
      networkStatus = 'Web Network';
    }

    try {
      App.addListener('appStateChange', ({ isActive }) => {
        if (isActive) {
          syncSnapshot();
        }
      });
    } catch (e) {
      // Web fallback
    }
  }

  async function syncSnapshot() {
    triggerHaptic();
    syncStatus = 'Syncing P2P...';

    // 1. Try Desktop P2P Pull over LAN / Tailscale
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);

      const res = await fetch(`http://${desktopIp}:8080/api/v1/portfolio/snapshot`, {
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        snapshot = await res.json();
        syncStatus = 'Synced via Desktop P2P';
        await Preferences.set({ key: 'portfolio_snapshot', value: JSON.stringify(snapshot) });
        recalculateLiveNavs();
        return;
      }
    } catch (e) {
      // Desktop unreachable, use local cached snapshot
    }

    // 2. Load from Native Capacitor Preferences
    try {
      const { value } = await Preferences.get({ key: 'portfolio_snapshot' });
      if (value) {
        snapshot = JSON.parse(value);
        syncStatus = 'Offline Cache (AMFI Live)';
        recalculateLiveNavs();
        return;
      }
    } catch (e) {
      // Ignore
    }

    syncStatus = 'Desktop Offline';
  }

  async function recalculateLiveNavs() {
    if (!snapshot || !snapshot.holdings) return;

    try {
      const navMap = await fetchLatestAmfiNavs();
      let total = 0;

      for (const h of snapshot.holdings) {
        const amfi = navMap.get(h.assetId);
        if (amfi && amfi.nav) {
          const val = parseFloat(h.investedValue) * (amfi.nav / parseFloat(h.lots[0]?.costPerUnit || '1'));
          total += isNaN(val) ? parseFloat(h.currentValue) : val;
        } else {
          total += parseFloat(h.currentValue);
        }
      }

      liveValuation = total || parseFloat(snapshot.totalCurrentValue);
    } catch (e) {
      liveValuation = parseFloat(snapshot.totalCurrentValue);
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
    <div class="chip-group">
      <span class="m3-chip m3-chip-tertiary">{networkStatus}</span>
      <span class="m3-chip m3-chip-primary">{syncStatus}</span>
    </div>
  </header>

  {#if snapshot}
    <!-- Hero Net Worth Card (Material 3 Dynamic Expressive Card) -->
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

      <div class="hero-footer">
        <div class="hero-metric">
          <span class="lbl">Unrealized Gain</span>
          <span class="val positive font-mono">+₹ {Math.round(parseFloat(snapshot.totalUnrealizedGain)).toLocaleString('en-IN')}</span>
        </div>
        <div class="hero-metric">
          <span class="lbl">Invested Basis</span>
          <span class="val font-mono">₹ {Math.round(parseFloat(snapshot.totalInvested)).toLocaleString('en-IN')}</span>
        </div>
      </div>
    </section>

    <!-- Asset Allocation Quick Glance -->
    <div class="section-title">
      <h2>Holdings & Open Lots ({snapshot.holdings.length})</h2>
      <span class="sub">Material You Dynamic Lots</span>
    </div>

    <section class="holdings-stack">
      {#each snapshot.holdings as h}
        <div class="m3-card holding-card" onclick={() => toggleExpand(h.assetId)}>
          <div class="holding-main">
            <div class="holding-info">
              <span class="m3-chip category-chip">{h.category.replace('_SPECIFIED_50AA', '')}</span>
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
                      <span class="date">{lot.acquisitionDate}</span>
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
    margin-bottom: 20px;
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
  .chip-group {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
  }
  .hero-card {
    background: linear-gradient(145deg, #181d2e, #111524);
    border: 1px solid rgba(192, 132, 252, 0.2);
    position: relative;
    overflow: hidden;
  }
  .hero-card::after {
    content: '';
    position: absolute;
    top: -40px;
    right: -40px;
    width: 120px;
    height: 120px;
    background: radial-gradient(circle, rgba(192, 132, 252, 0.25), transparent 70%);
    pointer-events: none;
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
    margin: 14px 0 18px 0;
    color: #ffffff;
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
  .category-chip {
    font-size: 9px;
    padding: 2px 8px;
    margin-bottom: 6px;
    background: rgba(255, 255, 255, 0.06);
    color: var(--md-sys-color-tertiary);
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
  .empty-card {
    text-align: center;
    padding: 40px 20px;
  }
  .pulse-icon {
    font-size: 36px;
    margin-bottom: 12px;
  }
</style>
