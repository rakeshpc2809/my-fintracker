import { e as escape_html } from "../../chunks/escaping.js";
import "clsx";
import "@capacitor/haptics";
import "@capacitor/status-bar";
import "@capacitor/app";
import "@capacitor/network";
import "@capacitor/preferences";
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    let syncStatus = "Initializing...";
    let networkStatus = "Checking network...";
    $$renderer2.push(`<main class="mobile-layout svelte-1uha8ag"><header class="m3-app-bar svelte-1uha8ag"><div class="brand-group svelte-1uha8ag"><div class="m3-avatar svelte-1uha8ag"></div> <div><h1 class="app-title svelte-1uha8ag">Portfolio OS</h1> <span class="m3-subtitle svelte-1uha8ag">Material 3 Expressive</span></div></div> <div class="chip-group svelte-1uha8ag"><span class="m3-chip m3-chip-tertiary">${escape_html(networkStatus)}</span> <span class="m3-chip m3-chip-primary">${escape_html(syncStatus)}</span></div></header> `);
    {
      $$renderer2.push("<!--[-1-->");
      $$renderer2.push(`<div class="m3-card empty-card svelte-1uha8ag"><div class="pulse-icon svelte-1uha8ag">⚡</div> <h3>Connecting to Desktop Core...</h3> <p>Connecting to 192.168.1.13:8080 over local Wi-Fi or Tailscale network.</p> <button class="m3-btn">Retry P2P Connection</button></div>`);
    }
    $$renderer2.push(`<!--]--></main>`);
  });
}
export {
  _page as default
};
