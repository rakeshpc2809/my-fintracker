import { CapacitorHttp } from '@capacitor/core';

export interface NavEntry {
  schemeCode: string;
  isin: string;
  schemeName: string;
  nav: number;
  date: string;
}

export async function fetchLatestAmfiNavs(): Promise<Map<string, NavEntry>> {
  const url = 'https://www.amfiindia.com/spages/NAVAll.txt';
  const navMap = new Map<string, NavEntry>();

  try {
    let text = '';

    // Native CapacitorHttp executes network call at Android Java layer, bypassing CORS
    try {
      const response = await CapacitorHttp.get({ url });
      if (response.status === 200 && response.data) {
        text = typeof response.data === 'string' ? response.data : String(response.data);
      }
    } catch (capErr) {
      // Browser fallback if CapacitorHttp plugin fails
      const res = await fetch(url);
      if (res.ok) {
        text = await res.text();
      }
    }

    if (!text) return navMap;

    const lines = text.split('\n');
    for (const line of lines) {
      const parts = line.split(';');
      if (parts.length >= 6) {
        const schemeCode = parts[0].trim();
        const isinGrowth = parts[1].trim();
        const isinReinvest = parts[2].trim();
        const schemeName = parts[3].trim();
        const navStr = parts[4].trim();
        const dateStr = parts[5].trim();

        const nav = parseFloat(navStr);
        if (!isNaN(nav)) {
          const entry: NavEntry = {
            schemeCode,
            isin: isinGrowth || isinReinvest,
            schemeName,
            nav,
            date: dateStr
          };

          if (isinGrowth) navMap.set(isinGrowth, entry);
          if (isinReinvest) navMap.set(isinReinvest, entry);
        }
      }
    }
  } catch (err) {
    console.error('Error fetching AMFI NAV feed on mobile:', err);
  }

  return navMap;
}
