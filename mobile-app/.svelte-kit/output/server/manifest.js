export const manifest = (() => {
function __memo(fn) {
	let value;
	return () => value ??= (value = fn());
}

return {
	appDir: "_app",
	appPath: "_app",
	assets: new Set([]),
	mimeTypes: {},
	_: {
		client: {start:"_app/immutable/entry/start.D9cJIvHh.js",app:"_app/immutable/entry/app.H68Aff7I.js",imports:["_app/immutable/entry/start.D9cJIvHh.js","_app/immutable/chunks/C13le7cF.js","_app/immutable/chunks/BCE62B9R.js","_app/immutable/chunks/BOHTsVM4.js","_app/immutable/entry/app.H68Aff7I.js","_app/immutable/chunks/BCE62B9R.js","_app/immutable/chunks/B4hMKib4.js","_app/immutable/chunks/DesNpNYd.js","_app/immutable/chunks/BOHTsVM4.js","_app/immutable/chunks/DK5boKXz.js","_app/immutable/chunks/AUv0EHW6.js"],stylesheets:[],fonts:[],uses_env_dynamic_public:false},
		nodes: [
			__memo(() => import('./nodes/0.js')),
			__memo(() => import('./nodes/1.js')),
			__memo(() => import('./nodes/2.js'))
		],
		remotes: {
			
		},
		routes: [
			{
				id: "/",
				pattern: /^\/$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 2 },
				endpoint: null
			}
		],
		prerendered_routes: new Set([]),
		matchers: async () => {
			
			return {  };
		},
		server_assets: {}
	}
}
})();
