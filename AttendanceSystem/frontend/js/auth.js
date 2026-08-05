

import api, { redirectOnce } from './api.js';

function parseToken(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (error) {
        return null;
    }
}

async function loginUser(userId, password, options = {}) {
    try {
        const response = await api.post('/auth/login', { identifier: userId, password }, options);

        if (response && response.token) {
            const token = response.token;
            api.setToken(token, response.role || '', response.userId || '', response.teacherId || '', response.expiresIn || 86400);
            return { success: true, role: response.role };
        }
        return { success: false, message: response.message || 'Invalid credentials' };
    } catch (error) {
        return { success: false, message: error.message };
    }
}

function logoutUser() {
    clearAuthStorage();
    api.post('/auth/logout').catch(err => console.warn("Logout audit failed: ", err));
    if (!window.location.pathname.includes('/login.html')) {
        redirectOnce('/login.html?reason=logout');
    }
}

function clearAuthStorage() {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userId');
    localStorage.removeItem('teacherId');
    localStorage.removeItem('tokenExpire');
    localStorage.removeItem('rememberMe');
    localStorage.clear();
    sessionStorage.clear();
}

function verifyRoleAccess(requiredRole) {
    const token = localStorage.getItem('authToken');
    const role = localStorage.getItem('userRole');

    if (!token || !role) {
        if (!window.location.pathname.includes('/login.html')) {
            redirectOnce('/login.html');
        }
        return false;
    }

    if (requiredRole) {
        if (Array.isArray(requiredRole)) {
            if (!requiredRole.includes(role)) {
                redirectBasedOnRole(role);
                return false;
            }
        } else {
            if (role !== requiredRole) {
                redirectBasedOnRole(role);
                return false;
            }
        }
    }

    return true;
}

function redirectBasedOnRole(role) {
    if (role === 'ADMIN') {
        redirectOnce('/admin-dashboard.html');
    } else if (role === 'TEACHER') {
        redirectOnce('/teacher-dashboard.html');
    } else {
        redirectOnce('/login.html');
    }
}

function getCurrentUser() {
    return {
        userId: localStorage.getItem('userId'),
        role: localStorage.getItem('userRole')
    };
}





let _inactivityTimer = null;
let _warningTimer = null;
let _countdownInterval = null;
let _activityListenersAttached = false;

function initSessionTimeout(role) {
    const TOTAL_TIMEOUT = 3 * 60 * 60 * 1000; // 3 Hours (10,800,000 ms)
    const WARNING_TIMEOUT = (3 * 60 - 5) * 60 * 1000; // 2 Hours 55 Mins (10,500,000 ms)

    function removeWarningModal() {
        clearInterval(_countdownInterval);
        const modal = document.getElementById('session-warning-modal');
        if (modal) modal.remove();
    }

    function showWarningModal() {
        if (document.getElementById('session-warning-modal')) return;
        let remainingSeconds = 300; // 5 minutes

        const modal = document.createElement('div');
        modal.id = 'session-warning-modal';
        modal.style.cssText = `
            position: fixed; top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center;
            z-index: 99999; font-family: var(--font-family, system-ui, sans-serif);
        `;
        modal.innerHTML = `
            <div style="background: white; border-radius: 12px; padding: 24px 32px; max-width: 420px; width: 90%; text-align: center; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.2);">
                <div style="width: 48px; height: 48px; background: #fef3c7; color: #d97706; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px;">
                    <svg style="width: 28px; height: 28px;" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                </div>
                <h3 style="margin: 0 0 8px; color: #1e293b; font-size: 20px; font-weight: 700;">Session Expiring Soon</h3>
                <p style="margin: 0 0 16px; color: #64748b; font-size: 14px; line-height: 1.5;">You have been inactive. For security, your session will end in <strong id="session-countdown-timer" style="color: #dc2626; font-size: 16px;">05:00</strong>.</p>
                <div style="display: flex; gap: 12px; justify-content: center;">
                    <button id="btn-extend-session" style="background: #2563eb; color: white; border: none; padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer;">Continue Session</button>
                    <button id="btn-logout-session" style="background: #e2e8f0; color: #475569; border: none; padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer;">Logout Now</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        document.getElementById('btn-extend-session')?.addEventListener('click', () => {
            removeWarningModal();
            resetTimer();
        });
        document.getElementById('btn-logout-session')?.addEventListener('click', () => {
            removeWarningModal();
            logoutUser();
        });

        _countdownInterval = setInterval(() => {
            remainingSeconds--;
            const mins = Math.floor(remainingSeconds / 60).toString().padStart(2, '0');
            const secs = (remainingSeconds % 60).toString().padStart(2, '0');
            const timerEl = document.getElementById('session-countdown-timer');
            if (timerEl) timerEl.textContent = `${mins}:${secs}`;
            if (remainingSeconds <= 0) {
                removeWarningModal();
                logoutUser();
            }
        }, 1000);
    }

    function resetTimer() {
        clearTimeout(_inactivityTimer);
        clearTimeout(_warningTimer);
        removeWarningModal();

        _warningTimer = setTimeout(showWarningModal, WARNING_TIMEOUT);
        _inactivityTimer = setTimeout(() => {
            removeWarningModal();
            clearAuthStorage();
            if (!window.location.pathname.includes('/login.html')) {
                redirectOnce('/login.html?reason=session_expired');
            }
        }, TOTAL_TIMEOUT);
    }

    if (!_activityListenersAttached) {
        let throttleTimer = null;
        ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click'].forEach(evt => {
            document.addEventListener(evt, () => {
                if (!throttleTimer) {
                    throttleTimer = setTimeout(() => {
                        throttleTimer = null;
                        if (!document.getElementById('session-warning-modal')) {
                            resetTimer();
                        }
                    }, 2000);
                }
            }, { passive: true });
        });
        _activityListenersAttached = true;
    }

    resetTimer();
}

export { loginUser, logoutUser, verifyRoleAccess, redirectBasedOnRole, getCurrentUser, initSessionTimeout };
