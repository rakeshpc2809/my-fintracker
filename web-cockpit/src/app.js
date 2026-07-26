const API_BASE = window.location.origin.includes('http') ? `${window.location.origin}/api/v1` : 'http://127.0.0.1:8080/api/v1';

let perfChart = null;
let allocChart = null;
let categoryChart = null;
let currentFy = '2026-27';

document.addEventListener('DOMContentLoaded', () => {
  const fySelect = document.getElementById('fySelect');
  if (fySelect) {
    currentFy = fySelect.value;
    fySelect.addEventListener('change', () => {
      currentFy = fySelect.value;
      fetchTaxMetrics();
      fetchRealizedLog();
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
          const msg = (result && result.message) ? result.message : 'Statement parsing failed. Please check file format.';
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

async function fetchLiveMetrics() {
  try {
    // 1. Portfolio summary & XIRR
    const summaryRes = await fetch(`${API_BASE}/portfolio/summary`);
    if (summaryRes.ok) {
      const summary = await summaryRes.json();
      updatePortfolioSummary(summary);
    }

    // 2. Tax metrics for current FY
    fetchTaxMetrics();

    // 3. Integrity check
    const integrityRes = await fetch(`${API_BASE}/events/integrity`);
    if (integrityRes.ok) {
      const data = await integrityRes.json();
      const statusPill = document.querySelector('.status-pill');
      if (statusPill && data.integrityValid) {
        statusPill.innerHTML = `<span class="status-dot"></span> SHA-256 Chain Intact (${data.latestHash.substring(0, 8)}...)`;
      }
    }

    // 4. Performance History Chart
    const historyRes = await fetch(`${API_BASE}/portfolio/history`);
    if (historyRes.ok) {
      const points = await historyRes.json();
      renderPerformanceChart(points);
    }

    // 5. Asset Allocations (Fund Level & Category)
    const allocRes = await fetch(`${API_BASE}/portfolio/allocation`);
    if (allocRes.ok) {
      const allocations = await allocRes.json();
      renderAllocationChart(allocations);
    }

    const catRes = await fetch(`${API_BASE}/portfolio/category-allocation`);
    if (catRes.ok) {
      const catAllocations = await catRes.json();
      renderCategoryChart(catAllocations);
    }

    // 6. Holdings Table with Open Lots
    const holdingsRes = await fetch(`${API_BASE}/portfolio/holdings`);
    if (holdingsRes.ok) {
      const holdings = await holdingsRes.json();
      renderHoldingsTable(holdings);
    }

    // 7. Decision Radar (Tax Harvest + Maturation Ladder)
    fetchDecisionRadar();

    // 8. Realized Disposals Log
    fetchRealizedLog();
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchTaxMetrics() {
  try {
    const exemptionRes = await fetch(`${API_BASE}/tax/exemption-status?fy=${currentFy}`);
    if (exemptionRes.ok) {
      const data = await exemptionRes.json();
      updateExemptionMeter(data);
    }

    const itr2Res = await fetch(`${API_BASE}/tax/reports/itr2?fy=${currentFy}`);
    if (itr2Res.ok) {
      const report = await itr2Res.json();
      updateReportMetrics(report);
    }
  } catch (e) {
    console.error('Error fetching tax metrics:', e);
  }
}

async function fetchDecisionRadar() {
  try {
    const harvestRes = await fetch(`${API_BASE}/tax/harvest-opportunities`);
    const opportunities = harvestRes.ok ? await harvestRes.json() : [];

    const ladderRes = await fetch(`${API_BASE}/tax/maturation-ladder`);
    const ladder = ladderRes.ok ? await ladderRes.json() : [];

    renderDecisionRadar(opportunities, ladder);
  } catch (e) {
    console.error('Error fetching decision radar:', e);
  }
}

async function fetchRealizedLog() {
  try {
    const logRes = await fetch(`${API_BASE}/tax/realized-log?fy=${currentFy}`);
    if (logRes.ok) {
      const logs = await logRes.json();
      renderRealizedLogTable(logs);
    }
  } catch (e) {
    console.error('Error fetching realized log:', e);
  }
}

function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  if (netWorthVal && summary.totalCurrentValue) {
    const val = Math.round(parseFloat(summary.totalCurrentValue) || 0);
    netWorthVal.textContent = `₹ ${val.toLocaleString('en-IN')}`;
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && summary.totalUnrealizedGain) {
    const gain = Math.round(parseFloat(summary.totalUnrealizedGain) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}₹ ${gain.toLocaleString('en-IN')}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && summary.activeHoldingCount !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${summary.activeHoldingCount} Schemes</strong>`;
  }
  if (xirrVal && summary.xirrPercentage) {
    xirrVal.textContent = summary.xirrPercentage;
    xirrVal.classList.remove('skeleton');
  }
}

function updateExemptionMeter(data) {
  const meterVal = document.querySelector('.ltcg-meter-val');
  const fill = document.querySelector('.progress-fill-gradient');
  const pctText = document.querySelector('.meter-meta .pct-used');
  const remainingText = document.querySelector('.meter-meta .remaining');

  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(data.exemptionUsed) || 0);
    const limit = Math.round(parseFloat(data.exemptionLimit) || 125000);
    const pct = Math.min(100, Math.round((used / limit) * 100));

    meterVal.innerHTML = `₹ ${used.toLocaleString('en-IN')} <span class="sub-limit">/ 1.25L</span>`;
    meterVal.classList.remove('skeleton');
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `₹ ${Math.round(limit - used).toLocaleString('en-IN')} Available`;
  }
}

function updateReportMetrics(report) {
  const stcgVal = document.querySelector('.stcg-val');
  if (stcgVal && report.totalRealizedStcg) {
    const stcg = Math.round(parseFloat(report.totalRealizedStcg) || 0);
    stcgVal.textContent = `₹ ${stcg.toLocaleString('en-IN')}`;
    stcgVal.classList.remove('skeleton');
  }
}

function renderDecisionRadar(opportunities, ladder) {
  const listContainer = document.querySelector('.radar-list');
  if (!listContainer) return;

  let html = '';

  // Render Tax Loss Harvesting Opportunities
  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const loss = Math.round(parseFloat(opp.potentialHarvestableLoss) || 0);
      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>₹${loss.toLocaleString('en-IN')}</strong> loss in <em>${opp.assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }

  // Render LTCG Maturation Ladder Opportunities
  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${mat.assetName}</em> (${mat.remainingUnits} units) becomes <strong>LTCG</strong> on ${mat.targetLtcgDate}.</div>
          </div>
          <span class="days-badge">Wait ${mat.daysRemainingToLtcg} Days</span>
        </div>
      `;
    }
  }

  if (!html) {
    html = `
      <div class="radar-card info-border">
        <div class="radar-icon info">✓</div>
        <div class="radar-content">
          <div class="radar-title">Portfolio Tax Status Optimal</div>
          <div class="radar-desc">No immediate tax-loss harvesting or pending LTCG transitions in the next 90 days.</div>
        </div>
        <span class="days-badge">Optimum</span>
      </div>
    `;
  }

  listContainer.innerHTML = html;
}

function renderHoldingsTable(holdings) {
  const tableBody = document.querySelector('#holdingsTable tbody');
  if (!tableBody) return;

  if (!holdings || holdings.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#64748b;">No open holdings found in ledger.</td></tr>`;
    return;
  }

  let html = '';
  holdings.forEach((h, idx) => {
    const inv = Math.round(parseFloat(h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr class="holding-row" onclick="toggleLotDetails('${idx}')">
        <td style="font-weight:600;">${h.assetName}</td>
        <td><span class="cat-badge cat-${h.category}">${h.category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">₹ ${inv.toLocaleString('en-IN')}</td>
        <td class="font-mono" style="font-weight:600;">₹ ${cur.toLocaleString('en-IN')}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}₹ ${gain.toLocaleString('en-IN')} (${h.unrealizedGainPct}%)</td>
        <td class="font-mono">${h.allocationPct}%</td>
        <td><button class="pill-btn">${h.lots.length} Lots ▼</button></td>
      </tr>
      <tr id="lotRow-${idx}" style="display: none;">
        <td colspan="7" class="lot-expansion-td">
          <table class="lot-subtable">
            <thead>
              <tr>
                <th>Acq Date</th>
                <th>Units</th>
                <th>Cost/Unit</th>
                <th>Cost Basis</th>
                <th>Current NAV</th>
                <th>Current Value</th>
                <th>Unrealized Gain</th>
                <th>Holding Days</th>
                <th>LTCG Status</th>
              </tr>
            </thead>
            <tbody>
              ${h.lots.map(l => `
                <tr>
                  <td>${l.acquisitionDate}</td>
                  <td>${l.remainingUnits}</td>
                  <td>₹ ${l.costPerUnit}</td>
                  <td>₹ ${Math.round(parseFloat(l.totalCostBasis)).toLocaleString('en-IN')}</td>
                  <td>₹ ${l.currentNav}</td>
                  <td>₹ ${Math.round(parseFloat(l.currentValue)).toLocaleString('en-IN')}</td>
                  <td style="${parseFloat(l.unrealizedGain) >= 0 ? 'color:#10b981' : 'color:#ef4444'}">
                    ${parseFloat(l.unrealizedGain) >= 0 ? '+' : ''}₹ ${Math.round(parseFloat(l.unrealizedGain)).toLocaleString('en-IN')}
                  </td>
                  <td>${l.holdingDays}d</td>
                  <td><span class="cat-badge ${l.isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.isLtcg ? 'LTCG' : 'STCG (' + (l.daysToLtcg > 0 ? l.daysToLtcg + 'd left' : 'Always') + ')'}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </td>
      </tr>
    `;
  });

  tableBody.innerHTML = html;
}

window.toggleLotDetails = (idx) => {
  const row = document.getElementById(`lotRow-${idx}`);
  if (row) {
    row.style.display = row.style.display === 'none' ? 'table-row' : 'none';
  }
};

function renderRealizedLogTable(logs) {
  const tableBody = document.querySelector('#realizedLogTable tbody');
  if (!tableBody) return;

  if (!logs || logs.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:#64748b;">No realized disposals recorded for ${currentFy}.</td></tr>`;
    return;
  }

  let html = '';
  logs.forEach(l => {
    const gain = Math.round(parseFloat(l.realizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr>
        <td>${l.disposalDate}</td>
        <td>${l.acquisitionDate}</td>
        <td style="font-weight:600;">${l.assetName}</td>
        <td class="font-mono">${l.unitsMatched}</td>
        <td class="font-mono">₹ ${Math.round(parseFloat(l.saleProceeds)).toLocaleString('en-IN')}</td>
        <td class="font-mono">₹ ${Math.round(parseFloat(l.costBasis)).toLocaleString('en-IN')}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}₹ ${gain.toLocaleString('en-IN')}</td>
        <td><span class="cat-badge ${l.taxTerm === 'LONG_TERM' ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.taxTerm}</span></td>
      </tr>
    `;
  });

  tableBody.innerHTML = html;
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

function renderCategoryChart(catAllocations) {
  const ctx = document.getElementById('categoryChart');
  if (!ctx || !catAllocations || catAllocations.length === 0) return;

  const labels = catAllocations.map(c => c.categoryName);
  const values = catAllocations.map(c => parseFloat(c.currentValue) || 0);

  if (categoryChart) categoryChart.destroy();

  categoryChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: labels,
      datasets: [{
        data: values,
        backgroundColor: [
          '#10b981', '#f59e0b', '#eab308', '#8b5cf6', '#06b6d4'
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

function showToast(message, type = 'success') {
  const stack = document.getElementById('toastStack');
  if (!stack) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  stack.appendChild(toast);

  setTimeout(() => {
    toast.remove();
  }, 4000);
}
