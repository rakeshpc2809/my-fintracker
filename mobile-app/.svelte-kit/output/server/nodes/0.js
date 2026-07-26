

export const index = 0;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/fallbacks/layout.svelte.js')).default;
export const imports = ["_app/immutable/nodes/0.CeYW1e16.js","_app/immutable/chunks/DesNpNYd.js","_app/immutable/chunks/BCE62B9R.js","_app/immutable/chunks/AUv0EHW6.js"];
export const stylesheets = [];
export const fonts = [];
