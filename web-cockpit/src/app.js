const API_BASE = window.location.origin.includes('http') ? `${window.location.origin}/api/v1` : 'http://127.0.0.1:8080/api/v1';

let perfChart = null;
let allocChart = null;

document.addEventListener('DOMContentLoaded', () => {
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

      try {
        const uploadBtn = document.querySelector('.upload-btn');
        if (uploadBtn) uploadBtn.textContent = 'Parsing Statement...';

        const res = await fetch(`${API_BASE}/statements/upload`, {
          method: 'POST',
          body: formData
        });

        if (res.ok) {
          const result = await res.json();
          alert(`Statement Ingested Successfully! ${result.message}`);
          fetchLiveMetrics();
        } else {
          alert('Statement parsing failed. Please check file format.');
        }
      } catch (err) {
        alert(`Error uploading statement: ${err.message}`);
      } finally {
        const uploadBtn = document.querySelector('.upload-btn');
        if (uploadBtn) uploadBtn.textContent = 'Upload CAS PDF / CSV';
        fileInput.value = '';
      }
    });
  }
});

async function fetchLiveMetrics() {
  try {
    // 1. Portfolio summary & XIRR
    const summaryRes = await fetch(`${API_BASE}/portfolio/summary`);
    if (summaryRes.ok) {
      const summary = await summaryRes.json();
      updatePortfolioSummary(summary);
    }

    // 2. Exemption status
    const exemptionRes = await fetch(`${API_BASE}/tax/exemption-status?fy=2026-27`);
    if (exemptionRes.ok) {
      const data = await exemptionRes.json();
      updateExemptionMeter(data);
    }

    // 3. Integrity check
    const integrityRes = await fetch(`${API_BASE}/events/integrity`);
    if (integrityRes.ok) {
      const data = await integrityRes.json();
      const statusPill = document.querySelector('.status-pill');
      if (statusPill && data.integrityValid) {
        statusPill.innerHTML = `<span class="status-dot"></span> SHA-256 Chain Intact (${data.latestHash.substring(0, 8)}...)`;
      }
    }

    // 4. ITR-2 report & STCG
    const itr2Res = await fetch(`${API_BASE}/tax/reports/itr2?fy=2026-27`);
    if (itr2Res.ok) {
      const report = await itr2Res.json();
      updateReportMetrics(report);
    }

    // 5. Performance History Chart
    const historyRes = await fetch(`${API_BASE}/portfolio/history`);
    if (historyRes.ok) {
      const points = await historyRes.json();
      renderPerformanceChart(points);
    }

    // 6. Asset Allocation Chart
    const allocRes = await fetch(`${API_BASE}/portfolio/allocation`);
    if (allocRes.ok) {
      const allocations = await allocRes.json();
      renderAllocationChart(allocations);
    }

    // 7. Harvest Advisor Opportunities
    const harvestRes = await fetch(`${API_BASE}/tax/harvest-opportunities`);
    if (harvestRes.ok) {
      const opportunities = await harvestRes.json();
      renderHarvestOpportunities(opportunities);
    }
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  if (netWorthVal && summary.totalCurrentValue) {
    const val = parseFloat(summary.totalCurrentValue) || 0;
    netWorthVal.textContent = `₹ ${Math.round(val).toLocaleString('en-IN')}`;
  }
  if (subText && summary.activeHoldingCount) {
    subText.innerHTML = `Active Holdings: <strong>${summary.activeHoldingCount} Schemes</strong>`;
  }
  if (xirrVal && summary.xirrPercentage) {
    xirrVal.textContent = summary.xirrPercentage;
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

    meterVal.innerHTML = `₹ ${Math.round(used).toLocaleString('en-IN')} <span class="sub-limit">/ 1.25L</span>`;
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `₹ ${Math.round(limit - used).toLocaleString('en-IN')} Available`;
  }
}

function updateReportMetrics(report) {
  const stcgVal = document.querySelector('.stcg-val');
  if (stcgVal && report.totalRealizedStcg) {
    const stcg = parseFloat(report.totalRealizedStcg) || 0;
    stcgVal.textContent = `₹ ${Math.round(stcg).toLocaleString('en-IN')}`;
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
          <div class="radar-desc">All open lots are currently sitting in gain or have 0 harvestable losses before March 31.</div>
        </div>
        <span class="days-badge">Optimum</span>
      </div>
    `;
    return;
  }

  let html = '';
  for (const opp of opportunities.slice(0, 3)) {
    const loss = Math.round(parseFloat(opp.potentialHarvestableLoss) || 0);
    html += `
      <div class="radar-card warning-border">
        <div class="radar-icon warning">⚡</div>
        <div class="radar-content">
          <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
          <div class="radar-desc">Harvest <strong>₹${loss.toLocaleString('en-IN')}</strong> loss in <em>${opp.assetName.substring(0, 30)}...</em> before March 31.</div>
        </div>
        <button class="action-btn">Inspect Lot</button>
      </div>
    `;
  }
  listContainer.innerHTML = html;
}

function renderPerformanceChart(points) {
  const ctx = document.getElementById('performanceChart');
  if (!ctx || !points || points.length === 0) return;

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
            label: (context) => ` ₹ ${Math.round(context.parsed.y).toLocaleString('en-IN')}`
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
  if (!ctx || !allocations || allocations.length === 0) return;

  const top7 = allocations.slice(0, 7);
  const labels = top7.map(a => a.assetName.substring(0, 20));
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
        }
      }
    }
  });
}
