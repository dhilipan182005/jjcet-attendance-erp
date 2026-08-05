

function showMessage(containerId, message, type = 'error') {
    const container = document.getElementById(containerId);
    if (!container) return;

    container.innerHTML = `
        <div class="alert alert-${type}">
            ${message}
        </div>
    `;
    container.classList.remove('hidden');

    setTimeout(() => {
        container.innerHTML = '';
        container.classList.add('hidden');
    }, 5000);
}

function clearForm(formId) {
    const form = document.getElementById(formId);
    if (form) {
        form.reset();
    }
}

function setupDashboard(logoutFunction, currentUser) {
    const userInfoEl = document.getElementById('user-info-display');
    if (userInfoEl && currentUser && currentUser.userId) {
        userInfoEl.textContent = currentUser.userId;
    }

    const logoutBtn = document.getElementById('btn-logout');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', (e) => {
            e.preventDefault();
            logoutFunction();
        });
    }
}

function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    let icon = '';
    switch(type) {
        case 'success': icon = '✓'; break;
        case 'error': icon = '✗'; break;
        case 'warning': icon = '⚠'; break;
        case 'info': icon = 'ℹ'; break;
    }

    toast.innerHTML = `<span style="font-size: 1.2em; font-weight: bold;">${icon}</span> <span>${escapeHTML(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('hiding');
        toast.addEventListener('animationend', () => {
            toast.remove();
        });
    }, 3000);
}

function initFormValidation() {
    document.querySelectorAll('form').forEach(form => {
        form.setAttribute('novalidate', true);
        form.querySelectorAll('input[required], select[required]').forEach(input => {
            let errorSpan = input.nextElementSibling;
            if (!errorSpan || !errorSpan.classList.contains('error-msg')) {
                errorSpan = document.createElement('span');
                errorSpan.className = 'error-msg hidden';
                input.parentNode.insertBefore(errorSpan, input.nextSibling);
            }

            input.addEventListener('blur', () => {
                validateInput(input, errorSpan);
            });
            input.addEventListener('input', () => {
                if (!errorSpan.classList.contains('hidden')) {
                    validateInput(input, errorSpan);
                }
            });
        });

        form.addEventListener('submit', (e) => {
            let isValid = true;
            form.querySelectorAll('input[required], select[required]').forEach(input => {
                const errorSpan = input.nextElementSibling;
                if (errorSpan && errorSpan.classList.contains('error-msg')) {
                    if (!validateInput(input, errorSpan)) {
                        isValid = false;
                    }
                }
            });

            if (!isValid) {
                e.preventDefault();
                e.stopImmediatePropagation();
                showToast('Please correct the highlighted fields.', 'error');
            }
        });
    });
}

function validateInput(input, errorSpan) {
    if (!input.checkValidity()) {
        input.classList.add('is-invalid');
        errorSpan.textContent = input.validationMessage || 'This field is required.';
        errorSpan.classList.remove('hidden');
        return false;
    } else {
        input.classList.remove('is-invalid');
        errorSpan.classList.add('hidden');
        return true;
    }
}

function debounce(func, delay) {
    let timeoutId;
    return function (...args) {
        if (timeoutId) clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
            func.apply(this, args);
        }, delay);
    };
}

function initTableSearch(inputId, tableId) {
    const input = document.getElementById(inputId);
    const table = document.getElementById(tableId);
    if (!input || !table) return;

    input.addEventListener('keyup', debounce(() => {
        const filter = input.value.toLowerCase();
        const trs = table.querySelectorAll('tbody tr');
        let visibleCount = 0;

        trs.forEach(tr => {
            if (tr.querySelector('.empty-state')) return;

            const text = tr.textContent.toLowerCase();
            if (text.includes(filter)) {
                tr.style.display = '';
                visibleCount++;
            } else {
                tr.style.display = 'none';
            }
        });

        let emptySearchRow = table.querySelector('.search-empty-state');
        if (visibleCount === 0 && filter.length > 0) {
            if (!emptySearchRow) {
                const tbody = table.querySelector('tbody');
                emptySearchRow = document.createElement('tr');
                emptySearchRow.className = 'search-empty-state';
                const colCount = table.querySelectorAll('thead th').length || 1;
                emptySearchRow.innerHTML = `<td colspan="${colCount}"><div class="empty-state"><h4>No Results Found</h4><p>No matches found for "${escapeHTML(input.value)}".</p></div></td>`;
                tbody.appendChild(emptySearchRow);
            } else {
                emptySearchRow.querySelector('p').textContent = `No matches found for "${input.value}".`;
                emptySearchRow.style.display = '';
            }
        } else if (emptySearchRow) {
            emptySearchRow.style.display = 'none';
        }
    }, 300));
}



export { showMessage, clearForm, setupDashboard, showToast, initFormValidation, initTableSearch };

export function extractArray(response) {
    if (Array.isArray(response)) return response;
    if (Array.isArray(response?.data)) return response.data;
    if (Array.isArray(response?.content)) return response.content;
    if (Array.isArray(response?.data?.content)) return response.data.content;
    return [];
}

export function populateSelect(
    select,
    records,
    {
        valueKey = "id",
        labelKey = "name",
        placeholder = "Select an option",
        preserveValue = true
    } = {}
) {
    if (!select) return;
    const previousValue = preserveValue ? select.value : "";
    let displayPlaceholder = placeholder;
    if (!records || records.length === 0) {
        if (placeholder.toLowerCase().includes("dept") || select.id.toLowerCase().includes("dept")) {
            displayPlaceholder = "No Departments Available";
        } else if (placeholder.toLowerCase().includes("batch") || select.id.toLowerCase().includes("batch")) {
            displayPlaceholder = "No Batches Available";
        }
    }
    select.innerHTML = `<option value="">${escapeHTML(displayPlaceholder)}</option>`;
    if (records && records.length > 0) {
        records.forEach(record => {
            const option = document.createElement("option");
            option.value = record[valueKey];
            option.textContent = record[labelKey];
            select.appendChild(option);
        });
    }
    if (
        preserveValue &&
        [...select.options].some(option => option.value === previousValue)
    ) {
        select.value = previousValue;
    }
}

const inFlightRequests = new Map();
export async function requestOnce(key, requestFactory) {
    if (inFlightRequests.has(key)) {
        return inFlightRequests.get(key);
    }
    const request = Promise.resolve()
        .then(requestFactory)
        .finally(() => {
            inFlightRequests.delete(key);
        });
    inFlightRequests.set(key, request);
    return request;
}

/**
 * Escapes HTML characters in a string to prevent XSS.
 * @param {string} str The string to escape.
 * @returns {string} The escaped string.
 */
export function escapeHTML(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
