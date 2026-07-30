const API_BASE = window.location.origin.includes('http') ? `${window.location.origin}/api/v1` : 'http://127.0.0.1:8080/api/v1';

export async function fetchSummary() {
  const res = await fetch(`${API_BASE}/portfolio/summary`);
  return res.ok ? await res.json() : null;
}

export async function fetchHoldings() {
  const res = await fetch(`${API_BASE}/portfolio/holdings`);
  return res.ok ? await res.json() : [];
}

export async function fetchAntigravityFactor() {
  const res = await fetch(`${API_BASE}/portfolio/antigravity`);
  return res.ok ? await res.json() : null;
}

export async function fetchExemptionStatus(fy) {
  const res = await fetch(`${API_BASE}/tax/exemption-status?fy=${fy}`);
  return res.ok ? await res.json() : null;
}

export async function fetchBucketRebalance() {
  const res = await fetch(`${API_BASE}/portfolio/buckets/rebalance`);
  return res.ok ? await res.json() : null;
}

export async function fetchGoalSummary() {
  const res = await fetch(`${API_BASE}/portfolio/goals`);
  return res.ok ? await res.json() : null;
}

export async function fetchFireSummary() {
  const res = await fetch(`${API_BASE}/portfolio/fire`);
  return res.ok ? await res.json() : null;
}

export async function fetchInsuranceStatus() {
  const res = await fetch(`${API_BASE}/portfolio/insurance`);
  return res.ok ? await res.json() : null;
}

export async function updateInsuranceItem(id, status) {
  const res = await fetch(`${API_BASE}/portfolio/insurance`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, status })
  });
  return res.ok ? await res.json() : null;
}

export async function fetchHistory() {
  const res = await fetch(`${API_BASE}/portfolio/history`);
  return res.ok ? await res.json() : [];
}

export async function fetchAllocation() {
  const res = await fetch(`${API_BASE}/portfolio/allocation`);
  return res.ok ? await res.json() : [];
}

export async function fetchCategoryAllocation() {
  const res = await fetch(`${API_BASE}/portfolio/category-allocation`);
  return res.ok ? await res.json() : [];
}

export async function fetchRealizedLogs(fy) {
  const res = await fetch(`${API_BASE}/tax/realized-log?fy=${fy}`);
  return res.ok ? await res.json() : [];
}

export async function fetchRebalancePreviewAmount(amount, fy) {
  const res = await fetch(`${API_BASE}/portfolio/rebalance-preview?amount=${amount}&fy=${fy}`);
  return res.ok ? await res.json() : null;
}

export async function fetchConsolidationPreview(fy) {
  const res = await fetch(`${API_BASE}/portfolio/consolidation-preview?fy=${fy}`);
  return res.ok ? await res.json() : null;
}
