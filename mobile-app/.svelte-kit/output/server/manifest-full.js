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
		client: {start:"_app/immutable/entry/start.BLKZ9g19.js",app:"_app/immutable/entry/app.BTgNVjFU.js",imports:["_app/immutable/entry/start.BLKZ9g19.js","_app/immutable/chunks/B6aNem6q.js","_app/immutable/chunks/CQKAw_f7.js","_app/immutable/chunks/C7gIPFeB.js","_app/immutable/entry/app.BTgNVjFU.js","_app/immutable/chunks/CenbtsBr.js","_app/immutable/chunks/CQKAw_f7.js","_app/immutable/chunks/KzbP3K05.js","_app/immutable/chunks/D2q5Rk5v.js","_app/immutable/chunks/C8t8npPT.js","_app/immutable/chunks/C7gIPFeB.js"],stylesheets:[],fonts:[],uses_env_dynamic_public:false},
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
