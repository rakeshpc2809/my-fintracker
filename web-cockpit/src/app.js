const API_BASE = window.location.origin.includes('http') ? `${window.location.origin}/api/v1` : 'http://127.0.0.1:8080/api/v1';

let perfChart = null;
let allocChart = null;
let currentFy = '2026-27';

document.addEventListener('DOMContentLoaded', () => {
  const fySelect = document.getElementById('fySelect');
  if (fySelect) {
    currentFy = fySelect.value;
    fySelect.addEventListener('change', () => {
      currentFy = fySelect.value;
      fetchTaxMetrics();
    });
  }

  fetchLiveMetrics();

  const fileInput = document.getElementById('fileUploadInput');
  if (fileInput) {
    fileInput.addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;

      let password = '';
      if (file.name.toLowerCase().endsWith('.pdf')) {
        password = prompt("Enter password for encrypted CAS PDF (usually PAN in lowercase or PAN + DOB):") || '';
      }

      const formData = new FormData();
      formData.append('file', file);
      if (password) {
        formData.append('password', password);
      }

      const uploadBtn = document.querySelector('.upload-btn');

      try {
        if (uploadBtn) uploadBtn.textContent = 'Parsing Statement...';

        const res = await fetch(`${API_BASE}/statements/upload`, {
          method: 'POST',
          body: formData
        });

        const result = await res.json().catch(() => null);

        if (res.ok && result && result.status === 'SUCCESS') {
          showToast(result.message || 'Statement ingested successfully.', 'success');
          fetchLiveMetrics();
        } else {
          const msg = (result && result.message) ? result.message : 'Statement parsing failed. Please check the file format.';
          showToast(msg, 'error');
        }
      } catch (err) {
        showToast(`Upload error: ${err.message}`, 'error');
      } finally {
        if (uploadBtn) uploadBtn.textContent = 'Upload CAS PDF / CSV';
        fileInput.value = '';
      }
    });
  }
});

// ---------- Toasts ----------

function showToast(message, type = 'success', timeoutMs = 6000) {
  const stack = document.getElementById('toastStack');
  if (!stack) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  stack.appendChild(toast);

  setTimeout(() => {
    toast.remove();
  }, timeoutMs);
}

// ---------- Formatting helpers ----------

function fmtInr(value) {
  const n = Math.round(parseFloat(value) || 0);
  return `₹ ${n.toLocaleString('en-IN')}`;
}

function truncate(str, maxLen) {
  if (!str) return '';
  return str.length > maxLen ? `${str.slice(0, maxLen)}…` : str;
}

function clearSkeleton(el) {
  if (el) el.classList.remove('skeleton');
}

// ---------- Fetch orchestration ----------

async function fetchLiveMetrics() {
  await Promise.all([
    fetchPortfolioSummary(),
    fetchTaxMetrics(),
    fetchIntegrityStatus(),
    fetchPerformanceHistory(),
    fetchAllocation(),
    fetchHarvestOpportunities()
  ]);
}

async function fetchTaxMetrics() {
  try {
    const exemptionRes = await fetch(`${API_BASE}/tax/exemption-status?fy=${currentFy}`);
    if (exemptionRes.ok) {
      updateExemptionMeter(await exemptionRes.json());
    }

    const itr2Res = await fetch(`${API_BASE}/tax/reports/itr2?fy=${currentFy}`);
    if (itr2Res.ok) {
      updateReportMetrics(await itr2Res.json());
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchPortfolioSummary() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/summary`);
    if (res.ok) {
      updatePortfolioSummary(await res.json());
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchIntegrityStatus() {
  try {
    const res = await fetch(`${API_BASE}/events/integrity`);
    if (res.ok) {
      const data = await res.json();
      const statusPill = document.querySelector('.status-pill');
      if (statusPill && data.integrityValid) {
        statusPill.innerHTML = `<span class="status-dot"></span> SHA-256 Chain Intact (${data.latestHash.substring(0, 8)}...)`;
      } else if (statusPill) {
        statusPill.innerHTML = `<span class="status-dot" style="background:#f87171;box-shadow:0 0 8px #f87171;"></span> Ledger Integrity Check Failed`;
      }
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchPerformanceHistory() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/history`);
    if (res.ok) {
      renderPerformanceChart(await res.json());
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchAllocation() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/allocation`);
    if (res.ok) {
      renderAllocationChart(await res.json());
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchHarvestOpportunities() {
  try {
    const res = await fetch(`${API_BASE}/tax/harvest-opportunities`);
    if (res.ok) {
      renderHarvestOpportunities(await res.json());
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

// ---------- Renderers ----------

function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainEl = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');
  const staleNote = document.getElementById('staleNavNote');

  if (netWorthVal && summary.totalCurrentValue) {
    netWorthVal.textContent = fmtInr(summary.totalCurrentValue);
    clearSkeleton(netWorthVal);
  }

  if (gainEl && summary.totalUnrealizedGain !== undefined) {
    const gain = parseFloat(summary.totalUnrealizedGain) || 0;
    const invested = parseFloat(summary.totalInvested) || 0;
    const pct = invested > 0 ? (gain / invested) * 100 : 0;
    const sign = gain > 0 ? '+' : '';
    const arrow = gain >= 0
      ? '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="18 15 12 9 6 15"></polyline></svg>'
      : '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="6 9 12 15 18 9"></polyline></svg>';

    gainEl.className = `metric-delta net-worth-gain ${gain > 0 ? 'positive' : gain < 0 ? 'negative' : 'neutral'}`;
    gainEl.innerHTML = `${arrow} ${sign}${fmtInr(Math.abs(gain))} (${sign}${pct.toFixed(1)}%)`;
  }

  if (subText && summary.activeHoldingCount !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${summary.activeHoldingCount} Schemes</strong>`;
  }

  if (xirrVal && summary.xirrPercentage) {
    xirrVal.textContent = summary.xirrPercentage;
    clearSkeleton(xirrVal);
  }

  if (staleNote) {
    if (summary.staleNavCount && summary.staleNavCount > 0) {
      staleNote.textContent = `${summary.staleNavCount} of ${summary.activeHoldingCount} holdings priced from last cost (NAV unavailable)`;
      staleNote.classList.add('visible');
    } else {
      staleNote.classList.remove('visible');
    }
  }
}

function updateExemptionMeter(data) {
  const meterVal = document.querySelector('.ltcg-meter-val');
  const fill = document.querySelector('.progress-fill-gradient');
  const pctText = document.querySelector('.meter-meta .pct-used');
  const remainingText = document.querySelector('.meter-meta .remaining');

  if (meterVal && fill && remainingText) {
    const used = parseFloat(data.exemptionUsed) || 0;
    const limit = parseFloat(data.exemptionLimit) || 125000;
    const pct = Math.min(100, Math.round((used / limit) * 100));

    meterVal.innerHTML = `${fmtInr(used)} <span class="sub-limit">/ 1.25L</span>`;
    clearSkeleton(meterVal);
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `${fmtInr(Math.max(0, limit - used))} Available`;
  }
}

function updateReportMetrics(report) {
  const stcgVal = document.querySelector('.stcg-val');
  if (stcgVal && report.totalRealizedStcg !== undefined) {
    stcgVal.textContent = fmtInr(report.totalRealizedStcg);
    clearSkeleton(stcgVal);
  }
}

function renderHarvestOpportunities(opportunities) {
  const listContainer = document.querySelector('.radar-list');
  if (!listContainer) return;

  if (!opportunities || opportunities.length === 0) {
    listContainer.innerHTML = `
      <div class="radar-card info-border">
        <div class="radar-icon info">✓</div>
        <div class="radar-content">
          <div class="radar-title">No Tax-Loss Harvesting Required</div>
          <div class="radar-desc">All open lots are currently sitting in gain or have 0 harvestable losses right now.</div>
        </div>
        <span class="days-badge">Optimum</span>
      </div>
    `;
    return;
  }

  let html = '';
  for (const opp of opportunities.slice(0, 3)) {
    const loss = Math.round(parseFloat(opp.potentialHarvestableLoss) || 0);
    const name = truncate(opp.assetName, 40);
    html += `
      <div class="radar-card warning-border">
        <div class="radar-icon warning">⚡</div>
        <div class="radar-content">
          <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
          <div class="radar-desc" title="${opp.assetName}">Harvest <strong>${fmtInr(loss)}</strong> unrealized loss in <em>${name}</em>.</div>
        </div>
        <button class="action-btn">Inspect Lot</button>
      </div>
    `;
  }
  listContainer.innerHTML = html;
}

function renderPerformanceChart(points) {
  const ctx = document.getElementById('performanceChart');
  const emptyState = document.getElementById('performanceEmpty');
  if (!ctx) return;

  if (!points || points.length === 0) {
    if (perfChart) { perfChart.destroy(); perfChart = null; }
    ctx.style.display = 'none';
    if (emptyState) emptyState.style.display = 'flex';
    return;
  }

  ctx.style.display = 'block';
  if (emptyState) emptyState.style.display = 'none';

  const labels = points.map(p => p.date);
  const values = points.map(p => parseFloat(p.invested) || 0);

  if (perfChart) perfChart.destroy();

  const chartCtx = ctx.getContext('2d');
  const gradient = chartCtx.createLinearGradient(0, 0, 0, 260);
  gradient.addColorStop(0, 'rgba(6, 182, 212, 0.35)');
  gradient.addColorStop(1, 'rgba(6, 182, 212, 0.0)');

  perfChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels,
      datasets: [{
        label: 'Invested Capital (₹)',
        data: values,
        borderColor: '#06b6d4',
        borderWidth: 3,
        backgroundColor: gradient,
        fill: true,
        tension: 0.3,
        pointRadius: 2,
        pointHoverRadius: 6
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#121a2b',
          borderColor: 'rgba(255, 255, 255, 0.1)',
          borderWidth: 1,
          callbacks: {
            label: (context) => ` ${fmtInr(context.parsed.y)}`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255, 255, 255, 0.03)' },
          ticks: { color: '#64748b', font: { family: 'Inter', size: 10 } }
        },
        y: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#64748b',
            font: { family: 'JetBrains Mono', size: 10 },
            callback: (val) => `₹ ${(val / 100000).toFixed(1)}L`
          }
        }
      }
    }
  });
}

function renderAllocationChart(allocations) {
  const ctx = document.getElementById('allocationChart');
  const emptyState = document.getElementById('allocationEmpty');
  if (!ctx) return;

  if (!allocations || allocations.length === 0) {
    if (allocChart) { allocChart.destroy(); allocChart = null; }
    ctx.style.display = 'none';
    if (emptyState) emptyState.style.display = 'flex';
    return;
  }

  ctx.style.display = 'block';
  if (emptyState) emptyState.style.display = 'none';

  const top7 = allocations.slice(0, 7);
  const labels = top7.map(a => truncate(a.assetName, 24) + (a.navStale ? ' *' : ''));
  const values = top7.map(a => parseFloat(a.currentValue) || 0);

  if (allocChart) allocChart.destroy();

  allocChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: labels,
      datasets: [{
        data: values,
        backgroundColor: [
          '#06b6d4', '#8b5cf6', '#f59e0b', '#10b981', '#ec4899', '#6366f1', '#14b8a6'
        ],
        borderWidth: 0
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'right',
          labels: { color: '#94a3b8', font: { family: 'Inter', size: 10 } }
        },
        tooltip: {
          callbacks: {
            label: (context) => {
              const entry = top7[context.dataIndex];
              const suffix = entry.navStale ? ' (priced at cost — NAV unavailable)' : '';
              return ` ${entry.assetName}: ${fmtInr(context.parsed)}${suffix}`;
            }
          }
        }
      }
    }
  });
}
