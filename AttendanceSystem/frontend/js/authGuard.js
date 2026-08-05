

import { verifyRoleAccess, getCurrentUser } from './auth.js';
import { redirectOnce } from './api.js';


export function checkAuthentication() {
    const token = localStorage.getItem('authToken');
    const expireTime = parseInt(localStorage.getItem('tokenExpire') || '0');

    if (!token || (expireTime > 0 && Date.now() > expireTime)) {
        localStorage.clear();
        if (!window.location.pathname.includes('/login.html')) {
            redirectOnce('/login.html?reason=unauthenticated');
        }
        return false;
    }
    return true;
}

window.addEventListener('pageshow', (event) => {
    if (event.persisted && !localStorage.getItem('authToken')) {
        if (!window.location.pathname.includes('/login.html')) {
            redirectOnce('/login.html?reason=unauthenticated');
        }
    }
});


export function checkAuthorization(requiredRoles) {
    if (!checkAuthentication()) return false;
    return verifyRoleAccess(requiredRoles);
}


export function validateSession() {
    const user = getCurrentUser();
    if (user && user.role) {
        import('./auth.js').then(authMod => {
            authMod.initSessionTimeout(user.role);
        });
    }
}


(function initGuard() {
    const publicPages = ['/index.html', '/login.html', '/401.html', '/403.html', '/404.html', '/500.html'];
    const path = window.location.pathname;
    const isPublic = publicPages.some(page => path === page || path.endsWith(page) || path === '');
    if (!isPublic && path !== '/' && path !== '') {
        checkAuthentication();
    }
})();
