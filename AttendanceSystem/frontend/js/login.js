

import { loginUser, redirectBasedOnRole } from './auth.js';
import { showAlert } from './alerts.js';
import { verifyBackendConnection } from './api.js';

document.addEventListener('DOMContentLoaded', () => {

    const existingToken = localStorage.getItem('authToken');
    const existingRole = localStorage.getItem('userRole');
    if (existingToken && existingRole) {
        redirectBasedOnRole(existingRole);
        return;
    }

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('reason') === 'session_expired') {
        showAlert('Session expired. Please login again.', 'error');
    } else if (urlParams.get('reason') === 'unauthorized') {
        showAlert('Your session has ended. Please login again.', 'error');
    }

    const loginForm = document.getElementById('login-form');
    const loginBtn = document.getElementById('login-btn');
    const togglePasswordBtn = document.getElementById('toggle-password');
    const passwordInput = document.getElementById('password');
    const rememberMeCheckbox = document.getElementById('remember-me');
    const userIdInput = document.getElementById('userId');

    if (localStorage.getItem('rememberedUserId')) {
        userIdInput.value = localStorage.getItem('rememberedUserId');
        if (rememberMeCheckbox) rememberMeCheckbox.checked = true;
    }

    if (togglePasswordBtn) {
        togglePasswordBtn.addEventListener('click', (e) => {
            e.preventDefault();
            passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
        });
    }

    let isSubmitting = false;
    let loginAbortController = null;

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (isSubmitting) return;

        const userId = userIdInput.value.trim();
        const password = passwordInput.value;

        if (!userId && !password) {
            showAlert('Please Enter User ID and Password', 'warning');
            return;
        } else if (!userId) {
            showAlert('Please Enter User ID', 'warning');
            return;
        } else if (!password) {
            showAlert('Please Enter Password', 'warning');
            return;
        }

        if (rememberMeCheckbox && rememberMeCheckbox.checked) {
            localStorage.setItem('rememberedUserId', userId);
        } else {
            localStorage.removeItem('rememberedUserId');
        }

        isSubmitting = true;
        loginBtn.disabled = true;
        loginBtn.querySelector('.btn-text').textContent = 'Signing in securely...';
        loginBtn.querySelector('.spinner').classList.remove('hidden');

        showAlert('Checking Credentials...', 'info', true);

        const slowLoadTimer = setTimeout(() => {
            if (isSubmitting) {
                loginBtn.querySelector('.btn-text').textContent = 'Starting secure server and signing you in...';
                showAlert('Starting secure server and signing you in...', 'info', true);
            }
        }, 15000);

        loginAbortController = new AbortController();

        try {
            verifyBackendConnection().catch(() => {});

            const result = await loginUser(userId, password, { 
                signal: loginAbortController.signal, 
                timeout: 90000 
            });

            if (result.success) {
                showAlert('Sign-in successful. Redirecting...', 'success', false);
                setTimeout(() => {
                    redirectBasedOnRole(result.role);
                }, 1000);
            } else {
                handleError(result.message || 'Invalid user ID/email address or password.');
            }
        } catch (error) {
            handleError(error.message || 'An unexpected error occurred while signing in. Please try again.');
        } finally {
            clearTimeout(slowLoadTimer);
            if (!document.querySelector('.alert.success')) {
                isSubmitting = false;
                loginBtn.disabled = false;
                loginBtn.querySelector('.btn-text').textContent = 'Sign In';
                loginBtn.querySelector('.spinner').classList.add('hidden');
            }
        }
    });

    function handleError(message) {
        isSubmitting = false;
        loginBtn.disabled = false;
        loginBtn.querySelector('.btn-text').textContent = 'Login';
        loginBtn.querySelector('.spinner').classList.add('hidden');
        showAlert(message, 'error');
    }
});
