import { e as escape_html } from "../../chunks/escaping.js";
import "clsx";
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    let syncStatus = "Offline Cache";
    $$renderer2.push(`<main class="mobile-container svelte-1uha8ag"><header class="mobile-header svelte-1uha8ag"><div class="brand svelte-1uha8ag"><div class="logo svelte-1uha8ag"></div> <div><h1 class="svelte-1uha8ag">Portfolio OS</h1> <span class="badge svelte-1uha8ag">Mobile Daily Driver (Svelte 5)</span></div></div> <span class="sync-pill svelte-1uha8ag">${escape_html(syncStatus)}</span></header> `);
    {
      $$renderer2.push("<!--[-1-->");
      $$renderer2.push(`<div class="card empty-card svelte-1uha8ag"><p>No snapshot synced yet. Connect to your desktop on Tailscale network to sync.</p></div>`);
    }
    $$renderer2.push(`<!--]--></main>`);
  });
}
export {
  _page as default
};
