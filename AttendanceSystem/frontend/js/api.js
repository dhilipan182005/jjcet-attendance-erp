


let redirectInProgress = false;
export function redirectOnce(url) {
    if (redirectInProgress) return;
    redirectInProgress = true;
    window.location.replace(url);
}

const getApiBaseUrl = () => {
    if (window.APP_CONFIG && window.APP_CONFIG.API_BASE_URL) {
        return window.APP_CONFIG.API_BASE_URL;
    }
    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
        return 'http://localhost:10000';
    }
    return 'https://jjcet-attendance-erp-4sb7.onrender.com';
};

export const RAW_BASE_URL = getApiBaseUrl();

export const API_BASE_URL = RAW_BASE_URL.endsWith('/api') ? RAW_BASE_URL : `${RAW_BASE_URL}/api`;

export const API_ORIGIN = API_BASE_URL.replace(/\/api$/, '');

export async function verifyBackendConnection() {
    try {
        const healthUrl = API_ORIGIN + '/actuator/health';
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 8000);
        const response = await fetch(healthUrl, { signal: controller.signal });
        clearTimeout(timeoutId);
        return response.ok;
    } catch (error) {
        return false;
    }
}

export class ApiError extends Error {
    constructor(message, status, code, validationErrors, data) {
        super(message);
        this.status = status;
        this.code = code || 'UNKNOWN_ERROR';
        this.validationErrors = validationErrors || {};
        this.data = data;
        this.name = 'ApiError';
    }
}

// Render's free tier can take 2-3 minutes to wake a sleeping instance. A single request
// with a short timeout will always fail during that window, so instead of giving up we
// poll the lightweight health endpoint until the backend responds (or we truly time out).
// Concurrent failed requests share one polling loop instead of each hammering /actuator/health.
let wakeupPromise = null;

function notify(message, type = 'error') {
    if (window.showToast) window.showToast(message, type);
}

export function waitForBackendWakeup(maxWaitMs = 150000, pollIntervalMs = 4000) {
    if (wakeupPromise) return wakeupPromise;

    const run = async () => {
        notify('Server is starting up. This can take up to 2 minutes on the first request...', 'info');
        const startedAt = Date.now();
        while (Date.now() - startedAt < maxWaitMs) {
            const ok = await verifyBackendConnection();
            if (ok) {
                notify('Connected. Loading your data now...', 'success');
                return true;
            }
            await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
        }
        notify('The server is taking longer than expected to start. Please try again shortly.', 'error');
        return false;
    };

    wakeupPromise = run().finally(() => { wakeupPromise = null; });
    return wakeupPromise;
}

class ApiManager {
    constructor() {
        this.baseURL = API_BASE_URL;
        this.tokenKey = "authToken";
        this.tokenExpireKey = "tokenExpire";
        this.roleKey = "userRole";
        this.userIdKey = "userId";
        this.teacherIdKey = "teacherId";
    }

    getToken() {
        const token = localStorage.getItem(this.tokenKey);
        const expireTime = localStorage.getItem(this.tokenExpireKey);
        if (!token) return null;
        if (expireTime && Date.now() > parseInt(expireTime)) {
            this.clearToken();
            if (!window.location.pathname.includes('/login.html') && !window.location.pathname.includes('/index.html')) {
                redirectOnce("/login.html?reason=unauthenticated");
            }
            return null;
        }
        return token;
    }

    setToken(token, role, userId, teacherId, expiresInSeconds = 86400) {
        localStorage.setItem(this.tokenKey, token);
        if (role) localStorage.setItem(this.roleKey, role);
        if (userId) localStorage.setItem(this.userIdKey, userId);
        if (teacherId) localStorage.setItem(this.teacherIdKey, teacherId);
        const expireTime = Date.now() + (expiresInSeconds * 1000);
        localStorage.setItem(this.tokenExpireKey, expireTime.toString());
    }

    clearToken() {
        localStorage.removeItem(this.tokenKey);
        localStorage.removeItem(this.tokenExpireKey);
        localStorage.removeItem(this.roleKey);
        localStorage.removeItem(this.userIdKey);
        localStorage.removeItem(this.teacherIdKey);
    }

    async request(endpoint, options = {}) {
        let normalizedEndpoint = endpoint ? endpoint.trim() : "";
        
        // Strip any leading '/api' from the endpoint so it's clean
        if (normalizedEndpoint.startsWith('/api/')) {
            normalizedEndpoint = normalizedEndpoint.substring(4);
        }
        if (!normalizedEndpoint.startsWith('/')) {
            normalizedEndpoint = '/' + normalizedEndpoint;
        }

        const url = `${this.baseURL}${normalizedEndpoint}`.replace(/([^:]\/)\/+/g, "$1");

        const token = this.getToken();
        const isFormData = (typeof FormData !== 'undefined') && options.body instanceof FormData;
        const headers = isFormData
            ? { ...options.headers }
            : { "Content-Type": "application/json", ...options.headers };

        const isPublicEndpoint = normalizedEndpoint === '/auth/login' || normalizedEndpoint === '/actuator/health';
        if (token && !isPublicEndpoint) {
            headers["Authorization"] = `Bearer ${token}`;
        }

        const fetchOptions = { ...options, headers };
        const isDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
        if (isDev) {
            console.log(`[API DEV] Request: ${url}`);
        }

        let attempt = 0;
        const isAuthRequest = normalizedEndpoint.includes('/auth/login');
        // Quick retries handle transient network blips. A separate wakeup-wait (below) handles
        // the much longer cold-start case instead of trying to cover both with one timeout.
        const maxRetries = isAuthRequest ? 1 : 2;
        const baseDelay = 2000;
        const timeoutDuration = options.timeout || 10000;
        let usedWakeupRetry = false;

        while (attempt < maxRetries) {
            const controller = new AbortController();
            if (options.signal) {
                options.signal.addEventListener('abort', () => controller.abort());
            }

            const timeoutId = setTimeout(() => controller.abort(), timeoutDuration);

            try {
                const { timeout, ...fetchArgs } = fetchOptions;
                const response = await fetch(url, { ...fetchArgs, signal: controller.signal });
                clearTimeout(timeoutId);
                if (isDev) {
                    console.log(`[API DEV DIAGNOSTIC] HTTP Status: ${response.status} ${response.statusText} for ${url}`);
                }

                let data = {};
                try {
                    const contentType = response.headers.get("content-type");
                    if (contentType && contentType.includes("application/json")) {
                        data = await response.json();
                    }
                } catch (e) {
                    // Ignore JSON parsing errors for empty bodies
                }

                if (response.status === 401) {
                    this.clearToken();
                    if (!window.location.pathname.includes('/login.html') && !window.location.pathname.includes('/index.html')) {
                        redirectOnce("/login.html?reason=unauthorized");
                    }
                    throw new ApiError(data.message || "Session expired.", 401, data.code, null, data);
                }

                if (response.status === 403) {
                    if (window.showToast) window.showToast("You do not have permission to access this resource.", "error");
                    throw new ApiError(data.message || "Access Denied", 403, data.code, null, data);
                }

                if (!response.ok) {
                    throw new ApiError(data.message || `API Error: ${response.status}`, response.status, data.code, data.validationErrors, data);
                }

                return data;
            } catch (error) {
                clearTimeout(timeoutId);

                const isTimeout = error.name === 'AbortError';
                const isConnectionError = error.name === 'TypeError' || error.message.includes('fetch') || isTimeout;

                if (isConnectionError && attempt < maxRetries - 1) {
                    attempt++;
                    console.warn(`Connection failed. Retry attempt ${attempt}...`);
                    await new Promise(resolve => setTimeout(resolve, baseDelay * attempt));
                    continue;
                }

                // Quick retries exhausted. If this looks like a Render cold start (not an auth
                // request, and we haven't already tried this) wait for the backend to actually
                // come up, then give the request one final real attempt.
                if (isConnectionError && !isAuthRequest && !usedWakeupRetry) {
                    usedWakeupRetry = true;
                    const becameReady = await waitForBackendWakeup();
                    if (becameReady) {
                        attempt = 0;
                        continue;
                    }
                }

                if (isTimeout) throw new Error('Unable to connect to server. Please try again.');
                if (isConnectionError) throw new Error('Unable to connect to server. Please try again.');
                throw error;
            }
        }
    }

    get(endpoint) {
        return this.request(endpoint, { method: "GET" });
    }

    post(endpoint, data, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: "POST",
            body: JSON.stringify(data)
        });
    }

    /** For multipart file uploads (e.g. CSV import). Pass a FormData instance. */
    uploadFile(endpoint, formData, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: "POST",
            body: formData
        });
    }

    put(endpoint, data) {
        return this.request(endpoint, {
            method: "PUT",
            body: JSON.stringify(data)
        });
    }

    deleteRequest(endpoint) {
        return this.request(endpoint, { method: "DELETE" });
    }
}


export const api = new ApiManager();
export default api;
window.api = api;
window.apiManager = api; 
