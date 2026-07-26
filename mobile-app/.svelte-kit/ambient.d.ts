
// this file is generated — do not edit it


/// <reference types="@sveltejs/kit" />

/**
 * This module provides access to environment variables that are injected _statically_ into your bundle at build time and are limited to _private_ access.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Static environment variables are [loaded by Vite](https://vitejs.dev/guide/env-and-mode.html#env-files) from `.env` files and `process.env` at build time and then statically injected into your bundle at build time, enabling optimisations like dead code elimination.
 * 
 * **_Private_ access:**
 * 
 * - This module cannot be imported into client-side code
 * - This module only includes variables that _do not_ begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) _and do_ start with [`config.kit.env.privatePrefix`](https://svelte.dev/docs/kit/configuration#env) (if configured)
 * 
 * For example, given the following build time environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { ENVIRONMENT, PUBLIC_BASE_URL } from '$env/static/private';
 * 
 * console.log(ENVIRONMENT); // => "production"
 * console.log(PUBLIC_BASE_URL); // => throws error during build
 * ```
 * 
 * The above values will be the same _even if_ different values for `ENVIRONMENT` or `PUBLIC_BASE_URL` are set at runtime, as they are statically replaced in your code with their build time values.
 */
declare module '$env/static/private' {
	export const SVELTEKIT_FORK: string;
	export const NODE_ENV: string;
	export const LC_NUMERIC: string;
	export const npm_node_execpath: string;
	export const HG: string;
	export const npm_config_global_prefix: string;
	export const PATH: string;
	export const npm_config_noproxy: string;
	export const npm_config_allow_scripts: string;
	export const GDK_BACKEND: string;
	export const ANTIGRAVITY_PROJECT_ID: string;
	export const OLLAMA_INTEL_GPU: string;
	export const DBUS_SESSION_BUS_ADDRESS: string;
	export const npm_package_json: string;
	export const XDG_RUNTIME_DIR: string;
	export const LC_ALL: string;
	export const ANTIGRAVITY_TRAJECTORY_ID: string;
	export const FC_FONTATIONS: string;
	export const npm_execpath: string;
	export const npm_config_user_agent: string;
	export const QT_ENABLE_HIGHDPI_SCALING: string;
	export const ANTIGRAVITY_AGENT: string;
	export const PAGER: string;
	export const SHLVL: string;
	export const ANTIGRAVITY_SOURCE_METADATA: string;
	export const ANTIGRAVITY_SAFECLIS_DIR: string;
	export const VISUAL: string;
	export const XDG_DATA_DIRS: string;
	export const MANPAGER: string;
	export const CHROME_DEVTOOLS_MCP_JS: string;
	export const npm_config_prefix: string;
	export const XDG_SESSION_TYPE: string;
	export const LC_TELEPHONE: string;
	export const LC_PAPER: string;
	export const npm_config_global_ignore_file: string;
	export const DISPLAY: string;
	export const AGY_BROWSER_WS_URL: string;
	export const HOME: string;
	export const npm_lifecycle_event: string;
	export const LC_NAME: string;
	export const PWD: string;
	export const MAIL: string;
	export const LC_MONETARY: string;
	export const XDG_VTNR: string;
	export const ANTIGRAVITY_LS_ADDRESS: string;
	export const DESKTOP_SESSION: string;
	export const npm_config_local_prefix: string;
	export const npm_config_globalconfig: string;
	export const LC_ADDRESS: string;
	export const GIT_PAGER: string;
	export const ZES_ENABLE_SYSMAN: string;
	export const EDITOR: string;
	export const npm_config_userconfig: string;
	export const VSSCRIPT_PATH: string;
	export const COLOR: string;
	export const ANTIGRAVITY_CONVERSATION_ID: string;
	export const npm_config_init_module: string;
	export const LC_MEASUREMENT: string;
	export const npm_package_name: string;
	export const DISABLE_AUTO_UPDATE: string;
	export const XDG_SESSION_DESKTOP: string;
	export const npm_config_cache: string;
	export const NO_AT_BRIDGE: string;
	export const npm_config_node_gyp: string;
	export const SHELL: string;
	export const OLLAMA_API_BASE: string;
	export const QT_QPA_PLATFORMTHEME: string;
	export const ATUIN_TMUX_POPUP: string;
	export const NODE: string;
	export const LC_TIME: string;
	export const RUSTICL_ENABLE: string;
	export const ANV_VIDEO_DECODE: string;
	export const LOGNAME: string;
	export const npm_command: string;
	export const TERMINAL: string;
	export const MOZ_ENABLE_WAYLAND: string;
	export const WAYLAND_DISPLAY: string;
	export const LANG: string;
	export const _JAVA_AWT_WM_NONREPARENTING: string;
	export const XE_DEBUG: string;
	export const USER: string;
	export const VIRTUAL_ENV_DISABLE_PROMPT: string;
	export const ZSH_TMUX_AUTOSTARTED: string;
	export const MANROFFOPT: string;
	export const FZF_DEFAULT_OPTS: string;
	export const XDG_SEAT: string;
	export const npm_package_version: string;
	export const npm_lifecycle_script: string;
	export const _: string;
	export const XDG_CURRENT_DESKTOP: string;
	export const ANTIGRAVITY_CSRF_TOKEN: string;
	export const DEBUGINFOD_URLS: string;
	export const ATUIN_SHLVL: string;
	export const INIT_CWD: string;
	export const OLLAMA_VULKAN: string;
	export const XDG_SESSION_ID: string;
	export const DCONF_PROFILE: string;
	export const CHROME_DESKTOP: string;
	export const STARSHIP_SESSION_KEY: string;
	export const QT_QPA_PLATFORM: string;
	export const QT_AUTO_SCREEN_SCALE_FACTOR: string;
	export const ZSH_TMUX_AUTOSTART: string;
	export const LC_IDENTIFICATION: string;
	export const npm_config_npm_version: string;
	export const AGY_BROWSER_ACTIVE_PORT_FILE: string;
	export const MOTD_SHOWN: string;
	export const STARSHIP_SHELL: string;
	export const ONEAPI_DEVICE_SELECTOR: string;
	export const ATUIN_SESSION: string;
	export const TERM: string;
}

/**
 * This module provides access to environment variables that are injected _statically_ into your bundle at build time and are _publicly_ accessible.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Static environment variables are [loaded by Vite](https://vitejs.dev/guide/env-and-mode.html#env-files) from `.env` files and `process.env` at build time and then statically injected into your bundle at build time, enabling optimisations like dead code elimination.
 * 
 * **_Public_ access:**
 * 
 * - This module _can_ be imported into client-side code
 * - **Only** variables that begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) (which defaults to `PUBLIC_`) are included
 * 
 * For example, given the following build time environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { ENVIRONMENT, PUBLIC_BASE_URL } from '$env/static/public';
 * 
 * console.log(ENVIRONMENT); // => throws error during build
 * console.log(PUBLIC_BASE_URL); // => "http://site.com"
 * ```
 * 
 * The above values will be the same _even if_ different values for `ENVIRONMENT` or `PUBLIC_BASE_URL` are set at runtime, as they are statically replaced in your code with their build time values.
 */
declare module '$env/static/public' {
	
}

/**
 * This module provides access to environment variables set _dynamically_ at runtime and that are limited to _private_ access.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Dynamic environment variables are defined by the platform you're running on. For example if you're using [`adapter-node`](https://github.com/sveltejs/kit/tree/main/packages/adapter-node) (or running [`vite preview`](https://svelte.dev/docs/kit/cli)), this is equivalent to `process.env`.
 * 
 * **_Private_ access:**
 * 
 * - This module cannot be imported into client-side code
 * - This module includes variables that _do not_ begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) _and do_ start with [`config.kit.env.privatePrefix`](https://svelte.dev/docs/kit/configuration#env) (if configured)
 * 
 * > [!NOTE] In `dev`, `$env/dynamic` includes environment variables from `.env`. In `prod`, this behavior will depend on your adapter.
 * 
 * > [!NOTE] To get correct types, environment variables referenced in your code should be declared (for example in an `.env` file), even if they don't have a value until the app is deployed:
 * >
 * > ```env
 * > MY_FEATURE_FLAG=
 * > ```
 * >
 * > You can override `.env` values from the command line like so:
 * >
 * > ```sh
 * > MY_FEATURE_FLAG="enabled" npm run dev
 * > ```
 * 
 * For example, given the following runtime environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { env } from '$env/dynamic/private';
 * 
 * console.log(env.ENVIRONMENT); // => "production"
 * console.log(env.PUBLIC_BASE_URL); // => undefined
 * ```
 */
declare module '$env/dynamic/private' {
	export const env: {
		SVELTEKIT_FORK: string;
		NODE_ENV: string;
		LC_NUMERIC: string;
		npm_node_execpath: string;
		HG: string;
		npm_config_global_prefix: string;
		PATH: string;
		npm_config_noproxy: string;
		npm_config_allow_scripts: string;
		GDK_BACKEND: string;
		ANTIGRAVITY_PROJECT_ID: string;
		OLLAMA_INTEL_GPU: string;
		DBUS_SESSION_BUS_ADDRESS: string;
		npm_package_json: string;
		XDG_RUNTIME_DIR: string;
		LC_ALL: string;
		ANTIGRAVITY_TRAJECTORY_ID: string;
		FC_FONTATIONS: string;
		npm_execpath: string;
		npm_config_user_agent: string;
		QT_ENABLE_HIGHDPI_SCALING: string;
		ANTIGRAVITY_AGENT: string;
		PAGER: string;
		SHLVL: string;
		ANTIGRAVITY_SOURCE_METADATA: string;
		ANTIGRAVITY_SAFECLIS_DIR: string;
		VISUAL: string;
		XDG_DATA_DIRS: string;
		MANPAGER: string;
		CHROME_DEVTOOLS_MCP_JS: string;
		npm_config_prefix: string;
		XDG_SESSION_TYPE: string;
		LC_TELEPHONE: string;
		LC_PAPER: string;
		npm_config_global_ignore_file: string;
		DISPLAY: string;
		AGY_BROWSER_WS_URL: string;
		HOME: string;
		npm_lifecycle_event: string;
		LC_NAME: string;
		PWD: string;
		MAIL: string;
		LC_MONETARY: string;
		XDG_VTNR: string;
		ANTIGRAVITY_LS_ADDRESS: string;
		DESKTOP_SESSION: string;
		npm_config_local_prefix: string;
		npm_config_globalconfig: string;
		LC_ADDRESS: string;
		GIT_PAGER: string;
		ZES_ENABLE_SYSMAN: string;
		EDITOR: string;
		npm_config_userconfig: string;
		VSSCRIPT_PATH: string;
		COLOR: string;
		ANTIGRAVITY_CONVERSATION_ID: string;
		npm_config_init_module: string;
		LC_MEASUREMENT: string;
		npm_package_name: string;
		DISABLE_AUTO_UPDATE: string;
		XDG_SESSION_DESKTOP: string;
		npm_config_cache: string;
		NO_AT_BRIDGE: string;
		npm_config_node_gyp: string;
		SHELL: string;
		OLLAMA_API_BASE: string;
		QT_QPA_PLATFORMTHEME: string;
		ATUIN_TMUX_POPUP: string;
		NODE: string;
		LC_TIME: string;
		RUSTICL_ENABLE: string;
		ANV_VIDEO_DECODE: string;
		LOGNAME: string;
		npm_command: string;
		TERMINAL: string;
		MOZ_ENABLE_WAYLAND: string;
		WAYLAND_DISPLAY: string;
		LANG: string;
		_JAVA_AWT_WM_NONREPARENTING: string;
		XE_DEBUG: string;
		USER: string;
		VIRTUAL_ENV_DISABLE_PROMPT: string;
		ZSH_TMUX_AUTOSTARTED: string;
		MANROFFOPT: string;
		FZF_DEFAULT_OPTS: string;
		XDG_SEAT: string;
		npm_package_version: string;
		npm_lifecycle_script: string;
		_: string;
		XDG_CURRENT_DESKTOP: string;
		ANTIGRAVITY_CSRF_TOKEN: string;
		DEBUGINFOD_URLS: string;
		ATUIN_SHLVL: string;
		INIT_CWD: string;
		OLLAMA_VULKAN: string;
		XDG_SESSION_ID: string;
		DCONF_PROFILE: string;
		CHROME_DESKTOP: string;
		STARSHIP_SESSION_KEY: string;
		QT_QPA_PLATFORM: string;
		QT_AUTO_SCREEN_SCALE_FACTOR: string;
		ZSH_TMUX_AUTOSTART: string;
		LC_IDENTIFICATION: string;
		npm_config_npm_version: string;
		AGY_BROWSER_ACTIVE_PORT_FILE: string;
		MOTD_SHOWN: string;
		STARSHIP_SHELL: string;
		ONEAPI_DEVICE_SELECTOR: string;
		ATUIN_SESSION: string;
		TERM: string;
		[key: `PUBLIC_${string}`]: undefined;
		[key: `${string}`]: string | undefined;
	}
}

/**
 * This module provides access to environment variables set _dynamically_ at runtime and that are _publicly_ accessible.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Dynamic environment variables are defined by the platform you're running on. For example if you're using [`adapter-node`](https://github.com/sveltejs/kit/tree/main/packages/adapter-node) (or running [`vite preview`](https://svelte.dev/docs/kit/cli)), this is equivalent to `process.env`.
 * 
 * **_Public_ access:**
 * 
 * - This module _can_ be imported into client-side code
 * - **Only** variables that begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) (which defaults to `PUBLIC_`) are included
 * 
 * > [!NOTE] In `dev`, `$env/dynamic` includes environment variables from `.env`. In `prod`, this behavior will depend on your adapter.
 * 
 * > [!NOTE] To get correct types, environment variables referenced in your code should be declared (for example in an `.env` file), even if they don't have a value until the app is deployed:
 * >
 * > ```env
 * > MY_FEATURE_FLAG=
 * > ```
 * >
 * > You can override `.env` values from the command line like so:
 * >
 * > ```sh
 * > MY_FEATURE_FLAG="enabled" npm run dev
 * > ```
 * 
 * For example, given the following runtime environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://example.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { env } from '$env/dynamic/public';
 * console.log(env.ENVIRONMENT); // => undefined, not public
 * console.log(env.PUBLIC_BASE_URL); // => "http://example.com"
 * ```
 * 
 * ```
 * 
 * ```
 */
declare module '$env/dynamic/public' {
	export const env: {
		[key: `PUBLIC_${string}`]: string | undefined;
	}
}
