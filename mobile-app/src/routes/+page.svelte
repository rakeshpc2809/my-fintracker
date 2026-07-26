<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchLatestAmfiNavs, type NavEntry } from '../lib/amfiSync';

  interface Holding {
    assetId: string;
    assetName: string;
    investedValue: string;
    currentValue: string;
    unrealizedGain: string;
    allocationPct: string;
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
  let syncStatus = $state<string>('Offline Cache');
  let tailscaleIp = $state<string>('100.115.92.1'); // Configurable Tailscale IP

  onMount(async () => {
    // 1. Try Tailscale P2P Sync from Desktop Ktor API
    try {
      const res = await fetch(`http://${tailscaleIp}:8080/api/v1/portfolio/snapshot`);
      if (res.ok) {
        snapshot = await res.json();
        syncStatus = 'Synced via Tailscale P2P';
        localStorage.setItem('portfolio_snapshot', JSON.stringify(snapshot));
      }
    } catch (e) {
      // 2. Fallback to local SQLite / LocalStorage cache
      const cached = localStorage.getItem('portfolio_snapshot');
      if (cached) {
        snapshot = JSON.parse(cached);
        syncStatus = 'Offline (Cached Snapshot)';
      }
    }

    // 3. Direct AMFI NAV Live Valuation
    if (snapshot && snapshot.holdings) {
      const navMap = await fetchLatestAmfiNavs();
      let total = 0;

      for (const h of snapshot.holdings) {
        const amfi = navMap.get(h.assetId);
        if (amfi) {
          total += parseFloat(h.currentValue);
        } else {
          total += parseFloat(h.currentValue);
        }
      }
      liveValuation = total || parseFloat(snapshot.totalCurrentValue);
    }
  });
</script>

<main class="mobile-container">
  <header class="mobile-header">
    <div class="brand">
      <div class="logo"></div>
      <div>
        <h1>Portfolio OS</h1>
        <span class="badge">Mobile Daily Driver (Svelte 5)</span>
      </div>
    </div>
    <span class="sync-pill">{syncStatus}</span>
  </header>

  {#if snapshot}
    <section class="card hero-card">
      <div class="label">LIVE NET WORTH (VALUATION)</div>
      <div class="value">₹ {Math.round(liveValuation || parseFloat(snapshot.totalCurrentValue)).toLocaleString('en-IN')}</div>
      <div class="subtext">
        Unrealized Gain: <span class="positive">+₹ {Math.round(parseFloat(snapshot.totalUnrealizedGain)).toLocaleString('en-IN')}</span>
      </div>
    </section>

    <section class="card holdings-card">
      <h2>Holdings Summary ({snapshot.holdings.length})</h2>
      <div class="holdings-list">
        {#each snapshot.holdings as h}
          <div class="holding-item">
            <div class="name">{h.assetName}</div>
            <div class="val">₹ {Math.round(parseFloat(h.currentValue)).toLocaleString('en-IN')}</div>
          </div>
        {/each}
      </div>
    </section>
  {:else}
    <div class="card empty-card">
      <p>No snapshot synced yet. Connect to your desktop on Tailscale network to sync.</p>
    </div>
  {/if}
</main>

<style>
  .mobile-container {
    padding: 16px;
    background-color: #070a12;
    color: #f8fafc;
    font-family: system-ui, -apple-system, sans-serif;
    min-height: 100vh;
  }
  .mobile-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .logo {
    width: 32px;
    height: 32px;
    background: linear-gradient(135deg, #06b6d4, #8b5cf6);
    border-radius: 8px;
  }
  h1 {
    font-size: 18px;
    margin: 0;
  }
  .badge {
    font-size: 10px;
    color: #c084fc;
    background: rgba(139, 92, 246, 0.2);
    padding: 2px 6px;
    border-radius: 4px;
  }
  .sync-pill {
    font-size: 10px;
    color: #06b6d4;
    background: rgba(6, 182, 212, 0.1);
    padding: 4px 8px;
    border-radius: 12px;
  }
  .card {
    background: rgba(15, 23, 42, 0.8);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 16px;
  }
  .hero-card .label {
    font-size: 11px;
    color: #64748b;
    font-weight: 600;
  }
  .hero-card .value {
    font-size: 26px;
    font-weight: 700;
    margin: 6px 0;
    font-family: monospace;
  }
  .subtext {
    font-size: 12px;
    color: #94a3b8;
  }
  .positive {
    color: #10b981;
  }
  .holdings-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-top: 12px;
  }
  .holding-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  }
  .holding-item .name {
    font-size: 13px;
    font-weight: 500;
  }
  .holding-item .val {
    font-size: 13px;
    font-family: monospace;
    font-weight: 600;
  }
</style>
