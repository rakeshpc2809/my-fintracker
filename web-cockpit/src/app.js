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
      fetchRebalancePreview();
    });
  }

  fetchLiveMetrics();

  // Export ZIP button listener
  const exportZipBtn = document.getElementById('exportZipBtn');
  if (exportZipBtn) {
    exportZipBtn.addEventListener('click', () => {
      window.location.href = `${API_BASE}/tax/export/itr2/zip?fy=${currentFy}`;
      showToast(`Generating ITR-2 CSV Bundle (.zip) for ${currentFy}...`, 'success');
    });
  }

  // Rebalance Slider listener
  const slider = document.getElementById('rebalanceSlider');
  const sliderVal = document.getElementById('rebalanceSliderVal');
  if (slider && sliderVal) {
    slider.addEventListener('input', () => {
      const val = parseInt(slider.value) || 100000;
      sliderVal.textContent = `₹ ${val.toLocaleString('en-IN')}`;
      fetchRebalancePreview(val);
    });
  }

  // File Upload listener
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
    const summaryRes = await fetch(`${API_BASE}/portfolio/summary`);
    if (summaryRes.ok) {
      const summary = await summaryRes.json();
      updatePortfolioSummary(summary);
    }

    fetchTaxMetrics();

    const integrityRes = await fetch(`${API_BASE}/events/integrity`);
    if (integrityRes.ok) {
      const data = await integrityRes.json();
      const statusPill = document.querySelector('.status-pill');
      if (statusPill && data.integrityValid) {
        statusPill.innerHTML = `<span class="status-dot"></span> SHA-256 Chain Intact (${data.latestHash.substring(0, 8)}...)`;
      }
    }

    const historyRes = await fetch(`${API_BASE}/portfolio/history`);
    if (historyRes.ok) {
      const points = await historyRes.json();
      renderPerformanceChart(points);
    }

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

    const holdingsRes = await fetch(`${API_BASE}/portfolio/holdings`);
    if (holdingsRes.ok) {
      const holdings = await holdingsRes.json();
      renderHoldingsTable(holdings);
    }

    fetchDecisionRadar();
    fetchRealizedLog();
    fetchInsuranceChecklist();
    fetchGoalSummary();
    fetchFireSummary();
    fetchBucketRebalance();
    fetchConsolidationPreviewData();

    const slider = document.getElementById('rebalanceSlider');
    const amt = slider ? slider.value : 100000;
    fetchRebalancePreview(amt);
  } catch (err) {
    console.log('Ktor API offline or starting up, using cached cockpit metrics.');
  }
}

async function fetchConsolidationPreviewData() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/consolidation-preview?fy=${currentFy}`);
    if (res.ok) {
      const data = await res.json();
      renderConsolidationPlan(data);
    }
  } catch (e) {
    console.error('Error fetching consolidation preview:', e);
  }
}

function renderConsolidationPlan(data) {
  const container = document.getElementById('consolidationPlanContainer');
  const badge = document.getElementById('consolidationWindowBadge');
  if (!container) return;

  if (badge) {
    badge.textContent = data.isRebalanceWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${data.nextScheduledWindow}`;
    badge.style.color = data.isRebalanceWindowOpen ? '#10b981' : '#06b6d4';
  }

  const proceeds = Math.round(parseFloat(data.totalProceeds) || 256200);
  const taxDrag = Math.round(parseFloat(data.totalTaxDrag) || 0);

  let html = `
    <div style="margin-bottom:12px; font-size:13px;" class="font-mono">
      Unlocked Capital: <strong style="color:#06b6d4;">₹ ${proceeds.toLocaleString('en-IN')}</strong> | 
      Estimated Tax Drag: <strong style="color:#f59e0b;">₹ ${taxDrag.toLocaleString('en-IN')}</strong>
    </div>
    <table class="data-table" style="font-size:12px;">
      <thead>
        <tr>
          <th>Active 6-Fund Core Asset</th>
          <th>SIP Target %</th>
          <th>Pro-Rata Deployment Amount</th>
        </tr>
      </thead>
      <tbody>
  `;

  for (const alloc of data.proRataAllocations) {
    const amt = Math.round(parseFloat(alloc.deploymentAmount) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${alloc.assetName}</td>
        <td><span class="days-badge">${alloc.sipWeightPct}</span></td>
        <td class="font-mono" style="font-weight:600; color:#10b981;">₹ ${amt.toLocaleString('en-IN')}</td>
      </tr>
    `;
  }

  html += `</tbody></table>`;
  container.innerHTML = html;
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

async function fetchRebalancePreview(amount = 100000) {
  try {
    const res = await fetch(`${API_BASE}/portfolio/rebalance-preview?amount=${amount}&fy=${currentFy}`);
    if (res.ok) {
      const data = await res.json();
      updateRebalanceSummary(data);
    }
  } catch (e) {
    console.error('Error fetching rebalance preview:', e);
  }
}

function updateRebalanceSummary(data) {
  const rebTaxDrag = document.getElementById('rebTaxDrag');
  const rebEffRate = document.getElementById('rebEffRate');
  const rebLtcgHarvested = document.getElementById('rebLtcgHarvested');

  if (rebTaxDrag && data.totalTaxDrag) {
    const drag = Math.round(parseFloat(data.totalTaxDrag) || 0);
    rebTaxDrag.textContent = `₹ ${drag.toLocaleString('en-IN')}`;
  }
  if (rebEffRate && data.effectiveTaxRatePct) {
    rebEffRate.textContent = data.effectiveTaxRatePct;
  }
  if (rebLtcgHarvested && data.ltcgExemptionHarvested) {
    const harvested = Math.round(parseFloat(data.ltcgExemptionHarvested) || 0);
    rebLtcgHarvested.textContent = `₹ ${harvested.toLocaleString('en-IN')}`;
  }
}

async function fetchDecisionRadar() {
  try {
    const harvestRes = await fetch(`${API_BASE}/tax/harvest-opportunities`);
    const opportunities = harvestRes.ok ? await harvestRes.json() : [];

    const ladderRes = await fetch(`${API_BASE}/tax/maturation-ladder`);
    const ladder = ladderRes.ok ? await ladderRes.json() : [];

    const antiRes = await fetch(`${API_BASE}/portfolio/antigravity`);
    const antigravityData = antiRes.ok ? await antiRes.json() : null;

    renderDecisionRadar(opportunities, ladder, antigravityData);
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

function renderDecisionRadar(opportunities, ladder, antigravityData) {
  const listContainer = document.querySelector('.radar-list');
  if (!listContainer) return;

  let html = '';

  if (antigravityData && antigravityData.antigravityAssets && antigravityData.antigravityAssets.length > 0) {
    for (const ag of antigravityData.antigravityAssets) {
      html += `
        <div class="radar-card info-border" style="border-left: 3px solid #06b6d4; background: rgba(6, 182, 212, 0.08);">
          <div class="radar-icon info">🚀</div>
          <div class="radar-content">
            <div class="radar-title" style="color:#06b6d4;">ANTIGRAVITY DETECTED (${ag.assetName})</div>
            <div class="radar-desc">Beta: <strong>${ag.beta}</strong> | 30d TWR: <strong>+${ag.twr30dPct}%</strong> during market drawdown (${antigravityData.marketDrawdownPct}%). ${ag.recommendation}</div>
          </div>
          <span class="antigravity-badge">🚀 Low Beta + Alpha</span>
        </div>
      `;
    }
  }

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

  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');

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
                <th>Cost Basis</th>
                <th>Unrealized Gain</th>
                <th>Days Held</th>
                <th>Tax Term</th>
              </tr>
            </thead>
            <tbody>
              ${h.lots.map(l => `
                <tr>
                  <td>${l.acquisitionDate}</td>
                  <td class="font-mono">${l.remainingUnits}</td>
                  <td class="font-mono">₹ ${Math.round(parseFloat(l.costPerUnit) * parseFloat(l.remainingUnits)).toLocaleString('en-IN')}</td>
                  <td class="font-mono" style="${parseFloat(l.unrealizedGain) >= 0 ? 'color: #10b981;' : 'color: #ef4444;'}">
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

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
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

  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');

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

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}

function renderPerformanceChart(points) {
  const container = document.getElementById('performanceChart');
  if (!container || !points || points.length === 0 || !window.echarts) return;

  if (perfChart) perfChart.dispose();
  perfChart = window.echarts.init(container);

  const dates = points.map(p => p.date);
  const investedValues = points.map(p => parseFloat(p.invested) || 0);
  const marketValues = points.map(p => parseFloat(p.currentValue || p.marketValue || p.invested) || 0);

  const option = {
    backgroundColor: 'transparent',
    legend: {
      data: ['Market Value', 'Invested Amount'],
      textStyle: { color: '#94a3b8', fontSize: 11 },
      top: 0,
      right: 10
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      textStyle: { color: '#f8fafc' },
      formatter: (params) => {
        let result = `<strong>${params[0].name}</strong><br/>`;
        params.forEach(p => {
          const val = Math.round(p.value);
          result += `${p.seriesName}: <strong>₹ ${val.toLocaleString('en-IN')}</strong><br/>`;
        });
        return result;
      }
    },
    grid: { top: 30, right: 20, bottom: 30, left: 65 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
      axisLabel: { color: '#64748b', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.04)' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        formatter: (val) => `₹ ${(val / 100000).toFixed(1)}L`
      }
    },
    series: [
      {
        name: 'Market Value',
        data: marketValues,
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#10b981', width: 3 },
        areaStyle: {
          color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16, 185, 129, 0.35)' },
            { offset: 1, color: 'rgba(16, 185, 129, 0.0)' }
          ])
        }
      },
      {
        name: 'Invested Amount',
        data: investedValues,
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#06b6d4', width: 2, type: 'dashed' }
      }
    ]
  };
  perfChart.setOption(option);
}

function renderPieChart(containerId, chartRefSetter, data) {
  const container = document.getElementById(containerId);
  if (!container || !data || data.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: ₹ {c} ({d}%)' },
    series: [{
      type: 'pie',
      radius: ['45%', '75%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#070a12', borderWidth: 2 },
      label: { show: false },
      data: data
    }]
  };
  instance.setOption(option);
  return instance;
}

function renderAllocationChart(allocations) {
  if (allocChart) allocChart.dispose();

  const top6 = allocations.slice(0, 6);
  const remaining = allocations.slice(6);

  const data = top6.map(a => ({
    name: a.assetName.length > 22 ? a.assetName.substring(0, 20) + '...' : a.assetName,
    value: parseFloat(a.currentValue) || 0
  }));

  if (remaining.length > 0) {
    const othersVal = remaining.reduce((sum, a) => sum + (parseFloat(a.currentValue) || 0), 0);
    if (othersVal > 0) {
      data.push({
        name: `Others (${remaining.length})`,
        value: othersVal
      });
    }
  }

  allocChart = renderPieChart('allocationChart', null, data);
}

function renderCategoryChart(catAllocations) {
  if (categoryChart) categoryChart.dispose();

  const data = catAllocations.map(c => ({
    name: c.categoryName,
    value: parseFloat(c.currentValue) || 0
  }));

  categoryChart = renderPieChart('categoryChart', null, data);
}

// Global debounced resize listener for GPU-accelerated ECharts
let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (perfChart) perfChart.resize();
    if (allocChart) allocChart.resize();
    if (categoryChart) categoryChart.resize();
  }, 150);
});

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

async function fetchInsuranceChecklist() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/insurance`);
    if (res.ok) {
      const data = await res.json();
      renderInsuranceBanner(data);
    }
  } catch (e) { console.error('Insurance checklist error:', e); }
}

function renderInsuranceBanner(data) {
  const banner = document.getElementById('insuranceBanner');
  const itemsContainer = document.getElementById('insuranceItemsList');
  const badge = document.getElementById('insuranceStatusBadge');
  if (!banner || !itemsContainer) return;

  if (data.isAllPurchased) {
    banner.style.display = 'none';
    return;
  }

  banner.style.display = 'block';
  if (badge) badge.textContent = 'ACTION REQUIRED';

  let html = '';
  data.items.forEach(item => {
    const isPurchased = item.status === 'PURCHASED';
    html += `
      <div class="insurance-card">
        <div class="insurance-info">
          <div class="title">${item.name}</div>
          <div class="desc">${item.description}</div>
        </div>
        <button class="action-btn ${isPurchased ? 'purchased-btn' : ''}" onclick="toggleInsuranceStatus('${item.id}', '${isPurchased ? 'NOT_PURCHASED' : 'PURCHASED'}')">
          ${isPurchased ? '✓ Purchased' : 'Mark Purchased'}
        </button>
      </div>
    `;
  });
  itemsContainer.innerHTML = html;
}

window.toggleInsuranceStatus = async (id, status) => {
  try {
    const res = await fetch(`${API_BASE}/portfolio/insurance`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, status })
    });
    if (res.ok) {
      const updated = await res.json();
      renderInsuranceBanner(updated);
      showToast(`Updated ${id} insurance status`, 'success');
    }
  } catch (e) { showToast(`Error updating insurance: ${e.message}`, 'error'); }
};

async function fetchGoalSummary() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/goals`);
    if (res.ok) {
      const data = await res.json();
      renderGoalSummary(data);
    }
  } catch (e) { console.error('Goal summary error:', e); }
}

function renderGoalSummary(data) {
  const idleVal = document.querySelector('.idle-cash-val');
  const allocList = document.getElementById('goalAllocList');

  if (idleVal && data.unallocatedCash) {
    const idle = Math.round(parseFloat(data.unallocatedCash) || 0);
    idleVal.textContent = `₹ ${idle.toLocaleString('en-IN')}`;
    idleVal.classList.remove('skeleton');
  }

  if (allocList && data.goalAllocations) {
    let html = '';
    data.goalAllocations.forEach(a => {
      const amt = Math.round(parseFloat(a.allocatedAmount) || 0);
      html += `
        <div class="goal-row">
          <div>
            <strong>${a.goalTag}</strong> — <span class="text-muted">${a.holdingName}</span>
          </div>
          <div class="font-mono">₹ ${amt.toLocaleString('en-IN')}</div>
        </div>
      `;
    });

    const unalloc = Math.round(parseFloat(data.unallocatedCash) || 0);
    html += `
      <div class="goal-row idle-row">
        <div>
          <strong style="color:var(--cyan-bright);">UNALLOCATED (SITTING IDLE)</strong>
        </div>
        <div class="font-mono highlight-cyan" style="font-weight:700;">₹ ${unalloc.toLocaleString('en-IN')}</div>
      </div>
    `;

    allocList.innerHTML = html;
  }
}

async function fetchFireSummary() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/fire`);
    if (res.ok) {
      const data = await res.json();
      renderFireSummary(data);
    }
  } catch (e) { console.error('FIRE summary error:', e); }
}

function renderFireSummary(data) {
  const statusPill = document.getElementById('fireStatusPill');
  const scenarioLabel = document.getElementById('fireScenarioLabel');
  const totalNw = document.getElementById('fireTotalNw');
  const investableNw = document.getElementById('fireInvestableNw');
  const reqCorpus = document.getElementById('fireRequiredCorpus');
  const projCorpus = document.getElementById('fireProjectedCorpus');
  const reviewBanner = document.getElementById('fireReviewBanner');

  if (statusPill) {
    statusPill.textContent = data.status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ₹ ${Math.round(parseFloat(data.shortageOrSurplusAmount)).toLocaleString('en-IN')}`;
    statusPill.className = `fire-status-pill ${data.status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${data.activeScenarioLabel}`;

  if (totalNw) totalNw.textContent = `₹ ${Math.round(parseFloat(data.totalNetWorth)).toLocaleString('en-IN')}`;
  if (investableNw) investableNw.textContent = `₹ ${Math.round(parseFloat(data.fireInvestableNetWorth)).toLocaleString('en-IN')}`;
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(data.requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(data.projectedCorpusAtTargetAge) / 10000000).toFixed(2)} Cr`;

  if (reviewBanner) {
    reviewBanner.style.display = data.reviewDatePassed ? 'block' : 'none';
  }
}

async function fetchBucketRebalance() {
  try {
    const res = await fetch(`${API_BASE}/portfolio/buckets/rebalance`);
    if (res.ok) {
      const data = await res.json();
      renderBucketRebalance(data);
    }
  } catch (e) { console.error('Bucket rebalance error:', e); }
}

function renderBucketRebalance(data) {
  const drawdownTag = document.getElementById('drawdownTag');
  const bucketGrid = document.getElementById('bucketGrid');
  const recList = document.getElementById('bucketRecList');

  if (drawdownTag && data.drawdownStatus) {
    const dd = data.drawdownStatus;
    drawdownTag.textContent = `${dd.benchmarkName}: ${dd.drawdownPct}% Drawdown (Rungs: ${dd.activeRungsFired.length ? dd.activeRungsFired.join('%, ') + '%' : 'None'})`;
  }

  if (bucketGrid && data.bucketStatuses) {
    let html = '';
    data.bucketStatuses.forEach(b => {
      const val = Math.round(parseFloat(b.currentValue) || 0);
      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${b.isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${b.isDrifted ? 'warn' : 'ok'}">${b.isDrifted ? 'Drift: ' + b.driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">₹ ${val.toLocaleString('en-IN')}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${b.currentPct}% · Target: ${b.targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }

  if (recList && data.recommendations) {
    if (data.recommendations.length === 0) {
      recList.innerHTML = `<div style="color:var(--text-muted); font-size:12px; padding:6px 0;">All bucket allocations are balanced within the 5% drift band. No rebalance needed.</div>`;
      return;
    }

    let html = '';
    data.recommendations.forEach(r => {
      const amt = Math.round(parseFloat(r.amount) || 0);
      const isBuy = r.action === 'BUY';
      html += `
        <div class="reb-stat" style="padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.03);">
          <span>
            <strong style="${isBuy ? 'color:var(--green-positive);' : 'color:var(--amber-warn);'}">[${r.action}]</strong>
            <strong>${r.assetName}</strong> (${r.bucket.replace('_', ' ')})
            <span class="text-muted" style="font-size:11px;">via ${r.triggerType}</span>
          </span>
          <span class="font-mono" style="font-weight:600;">₹ ${amt.toLocaleString('en-IN')} ${r.estimatedTaxDrag !== '0.00' ? '(Tax Drag: ₹' + r.estimatedTaxDrag + ')' : ''}</span>
        </div>
      `;
    });
    recList.innerHTML = html;
  }
}

// Live Interactive Configurator Event Listeners
function initConfigurator() {
  const inputs = document.querySelectorAll('.config-input');
  inputs.forEach(input => {
    input.addEventListener('input', updateConfiguratorCalculations);
  });
}

function updateConfiguratorCalculations() {
  const fireAge = parseFloat(document.getElementById('cfgFireAge')?.value) || 45;
  const monthlyExpense = parseFloat(document.getElementById('cfgMonthlyExpense')?.value) || 60000;
  const swrPct = parseFloat(document.getElementById('cfgSwrPct')?.value) || 3.0;

  const eqCore = parseFloat(document.getElementById('cfgEquityCorePct')?.value) || 45;
  const eqSat = parseFloat(document.getElementById('cfgEquitySatPct')?.value) || 25;
  const gold = parseFloat(document.getElementById('cfgGoldPct')?.value) || 15;
  const liquid = parseFloat(document.getElementById('cfgLiquidPct')?.value) || 15;

  const totalTargetPct = eqCore + eqSat + gold + liquid;
  const msgEl = document.getElementById('configValidationMsg');
  if (msgEl) {
    if (Math.abs(totalTargetPct - 100) > 0.01) {
      msgEl.style.color = '#ef4444';
      msgEl.style.background = 'rgba(239, 68, 68, 0.1)';
      msgEl.textContent = `⚠️ Target percentages sum to ${totalTargetPct}% (Must equal 100%).`;
    } else {
      msgEl.style.color = 'var(--cyan-bright)';
      msgEl.style.background = 'rgba(34, 211, 238, 0.1)';
      msgEl.textContent = `✓ Allocation targets sum to 100%. Calculations updated live.`;
    }
  }

  // Update FIRE Card Stat Labels & Corpus
  const requiredCorpus = Math.round((monthlyExpense * 12) / (swrPct / 100));
  const fireReqEl = document.getElementById('fireRequiredCorpus');
  const fireExpSub = document.getElementById('fireExpenseSub');
  const scenarioLbl = document.getElementById('fireScenarioLabel');

  if (fireReqEl) {
    fireReqEl.textContent = `₹ ${requiredCorpus.toLocaleString('en-IN')}`;
  }
  if (fireExpSub) {
    fireExpSub.textContent = `${swrPct.toFixed(1)}% SWR @ ₹${Math.round(monthlyExpense/1000)}k/mo`;
  }
  if (scenarioLbl) {
    scenarioLbl.textContent = `Target Age ${fireAge} • Custom`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  initConfigurator();
});
