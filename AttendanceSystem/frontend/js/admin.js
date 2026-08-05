import { escapeHTML } from './utils.js';


import api, { API_BASE_URL } from './api.js';
import { verifyRoleAccess, logoutUser, getCurrentUser, initSessionTimeout } from './auth.js';
import { showToast, showMessage, clearForm, setupDashboard, initFormValidation, extractArray, populateSelect, requestOnce } from './utils.js';
import { formatDate, formatDateTime } from './date-utils.js';

let trendChart = null;
let todayPieChart = null;
let departmentBarChart = null;

let currentSearchPage = 0;
let currentSearchFilters = {};
let currentAuditPage = 0;
let analyticsPollingInterval = null;

window.__JJCET_FRONTEND_BUILD__ = "2026-07-09-router-debug-1";

let appState = 'NOT_STARTED';

async function initializeAdminApplication() {
    console.log('[ADMIN_BOOT:01] initializeAdminApplication started. Current appState=', appState);
    if (appState !== 'NOT_STARTED') {
        console.log('[ADMIN_BOOT:ABORT] reason=appState is not NOT_STARTED');
        return;
    }
    appState = 'INITIALIZING';
    console.log('[ADMIN_BOOT:02] verifyRoleAccess');

    if (!verifyRoleAccess(['ADMIN'])) {
        console.log('[ADMIN_BOOT:ABORT] reason=verifyRoleAccess failed');
        appState = 'FAILED';
        throw new Error("verifyRoleAccess failed");
    }

    try {
        console.log('[ADMIN_BOOT:03] setupDashboard');
        setupDashboard(logoutUser, getCurrentUser());
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] setupDashboard failed:', e);
    }

    try {
        console.log('[ADMIN_BOOT:04] initTabs');
        initTabs();
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] initTabs failed:', e);
    }

    try {
        console.log('[ADMIN_BOOT:05] initFormValidation');
        initFormValidation();
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] initFormValidation failed:', e);
    }

    try {
        console.log('[ADMIN_BOOT:06] setupEventListeners');
        setupEventListeners();
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] setupEventListeners failed:', e);
    }

    try {
        if (typeof initPasswordToggles === 'function') initPasswordToggles();
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] initPasswordToggles failed:', e);
    }

    try {
        initSessionTimeout('ADMIN');
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] initSessionTimeout failed:', e);
    }

    try {
        console.log('[ADMIN_BOOT:07] loadMasterData');
        await loadMasterData();
    } catch (e) {
        console.warn("[ADMIN_BOOT:WARN] Master data load issue:", e);
    }

    try {
        console.log('[ADMIN_BOOT:08] resolveCurrentRoute');
        resolveCurrentRoute();
    } catch (e) {
        console.error('[ADMIN_BOOT:ERR] resolveCurrentRoute failed:', e);
    }

    appState = 'READY';
    console.log('[ADMIN_BOOT:09] application ready');
}

async function bootstrapAdminApp() {
    try {
        await initializeAdminApplication();
    } catch (e) {
        console.error('[ADMIN_BOOT:FATAL]', e);
        const failureOverlay = document.getElementById('app-failure-overlay');
        if (failureOverlay) failureOverlay.style.display = 'flex';
    } finally {
        const loadingOverlay = document.getElementById('app-loading-overlay');
        if (loadingOverlay) loadingOverlay.style.display = 'none';
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrapAdminApp);
} else {
    bootstrapAdminApp();
}

window.onerror = function(message, source, lineno, colno, error) {
    console.error(`[ADMIN_BOOT:ERROR] window.onerror: ${escapeHTML(message)} at ${escapeHTML(source)}:${escapeHTML(lineno)}:${escapeHTML(colno)}`);
};
window.onunhandledrejection = function(event) {
    console.error(`[ADMIN_BOOT:ERROR] unhandledrejection:`, event.reason);
};

const routeLoaders = {
    '#analytics-center': loadAnalyticsCenter,
    '#manage-students': () => { if (typeof loadStudentsCRUD === 'function') loadStudentsCRUD(); },
    '#import-students': () => { if (typeof initCsvImportPage === 'function') initCsvImportPage(); },
    '#manage-teachers': () => { if (typeof loadTeachersCRUD === 'function') loadTeachersCRUD(); },
    '#manage-departments': async () => {
        try {
            const res = await requestOnce('depts', () => api.get('/admin/departments'));
            if (typeof renderDepartmentsCRUD === 'function') renderDepartmentsCRUD(extractArray(res));
        } catch(e) { console.error(e); }
    },
    '#manage-batches': async () => {
        try {
            const res = await requestOnce('batches', () => api.get('/admin/batches'));
            if (typeof renderBatchesCRUD === 'function') renderBatchesCRUD(extractArray(res));
        } catch(e) { console.error(e); }
    },
    '#unlock-sessions': () => { if (typeof loadLockStatus === 'function') loadLockStatus(); },
    '#attendance-reports': () => {
        const todayStr = new Date().toISOString().split('T')[0];
        const exportDateEl = document.getElementById('export-date');
        if (exportDateEl && !exportDateEl.value) exportDateEl.value = todayStr;
        const searchDateEl = document.getElementById('search-date');
        if (searchDateEl && !searchDateEl.value) searchDateEl.value = todayStr;
        if (typeof loadSearchDropdowns === 'function') loadSearchDropdowns();
        if (typeof loadAttendanceLogsSearch === 'function') loadAttendanceLogsSearch(0);
    },
    '#audit-logs': () => { if (typeof loadAuditLogs === 'function') loadAuditLogs(0); }
};

function initTabs() {
    window.addEventListener('hashchange', resolveCurrentRoute);
    
    const sidebar = document.querySelector('.dashboard-sidebar');
    if (sidebar) {
        sidebar.addEventListener('click', (e) => {
            if (e.target.closest('a')) {
                sidebar.classList.remove('sidebar-open');
            }
        });
    }
}

function resolveCurrentRoute() {
    const hash = window.location.hash || '#analytics-center';
    console.log('[ADMIN_BOOT:ROUTE] resolveCurrentRoute. Hash:', hash);
    
    const sidebarLinks = document.querySelectorAll('#sidebar-menu a');
    const sections = document.querySelectorAll('.admin-section, .tab-content');
    const pageTitleText = document.getElementById('page-title-text');

    let routeFound = false;

    sections.forEach(sec => {
        sec.classList.add('hidden');
        sec.style.display = 'none';
    });
    sidebarLinks.forEach(link => link.classList.remove('active'));

    let finalHash = hash;
    if (!routeLoaders[finalHash]) {
        finalHash = '#analytics-center';
        window.history.replaceState(null, '', finalHash);
    }
    
    console.log('[ADMIN_BOOT:ROUTE] Route resolved to:', finalHash);
    
    sidebarLinks.forEach(link => {
        if (link.getAttribute('href') === finalHash) {
            link.classList.add('active');
            if (pageTitleText) pageTitleText.textContent = link.textContent;
        }
    });

    try {
        const activeSection = document.querySelector(finalHash);
        if (activeSection) {
            activeSection.classList.remove('hidden');
            activeSection.style.display = ''; 
            console.log(`[ADMIN_BOOT:ROUTE] Displayed section ID=${escapeHTML(activeSection.id)}`);
        } else {
            console.error(`[ADMIN_BOOT:ROUTE_ERR] DOM element missing for ${escapeHTML(finalHash)}`);
        }
    } catch (e) {
        console.error(`[ADMIN_BOOT:ROUTE_ERR] Invalid hash selector: ${escapeHTML(finalHash)}`, e);
    }

    if (finalHash !== '#analytics-center' && analyticsPollingInterval) {
        clearInterval(analyticsPollingInterval);
        analyticsPollingInterval = null;
    }

    const loader = routeLoaders[finalHash];
    if (loader) {
        console.log(`[ADMIN_BOOT:ROUTE] Executing loader for ${escapeHTML(finalHash)}`);
        loader();
    }
}

function setupEventListeners() {
    document.getElementById('student-form').addEventListener('submit', handleCreateStudent);
    document.getElementById('teacher-form').addEventListener('submit', handleCreateTeacher);
    document.getElementById('dept-add-form').addEventListener('submit', handleAddDepartment);
    document.getElementById('batch-add-form').addEventListener('submit', handleAddBatch);
    const pwdInput = document.getElementById('t-password');
    if (pwdInput) {
        pwdInput.addEventListener('input', (e) => {
            const pwd = e.target.value;
            let strength = 0;
            if (pwd.length >= 12) strength += 25;
            if (/[A-Z]/.test(pwd)) strength += 25;
            if (/[a-z]/.test(pwd)) strength += 25;
            if (/[0-9]/.test(pwd) && /[^A-Za-z0-9]/.test(pwd)) strength += 25;
            const bar = document.getElementById('pwd-strength-bar');
            if (bar) {
                bar.style.width = strength + '%';
                if (strength < 50) bar.style.backgroundColor = 'var(--error-color)';
                else if (strength < 100) bar.style.backgroundColor = '#f59e0b';
                else bar.style.backgroundColor = 'var(--success-color)';
            }
        });
    }
    if (document.getElementById('unlock-session-form')) {
        const unlockDate = document.getElementById('unlock-date');
        const unlockSession = document.getElementById('unlock-session-select');
        if (unlockDate) unlockDate.addEventListener('change', () => { if (typeof loadLockStatus === 'function') loadLockStatus(); });
        if (unlockSession) unlockSession.addEventListener('change', () => { if (typeof loadLockStatus === 'function') loadLockStatus(); });
        if (typeof loadLockStatus === 'function') loadLockStatus();
    } 

    const searchForm = document.getElementById('search-filter-form');
    if (searchForm) {
        searchForm.addEventListener('submit', (e) => {
            e.preventDefault();
            currentSearchFilters = {
                studentName: document.getElementById('search-student-name').value.trim(),
                registerNumber: document.getElementById('search-register-number').value.trim(),
                departmentId: document.getElementById('search-dept').value,
                batchId: document.getElementById('search-batch').value,
                date: document.getElementById('search-date').value,
                session: document.getElementById('search-session').value,
                status: document.getElementById('search-status') ? document.getElementById('search-status').value : '',
                eveningClassEnabled: document.getElementById('search-evening-enabled') ? document.getElementById('search-evening-enabled').value : ''
            };
            loadAttendanceLogsSearch(0);
        });
    }

    document.getElementById('btn-search-prev').addEventListener('click', () => {
        if (currentSearchPage > 0) loadAttendanceLogsSearch(currentSearchPage - 1);
    });
    document.getElementById('btn-search-next').addEventListener('click', () => {
        loadAttendanceLogsSearch(currentSearchPage + 1);
    });

    document.getElementById('btn-audit-prev').addEventListener('click', () => {
        if (currentAuditPage > 0) loadAuditLogs(currentAuditPage - 1);
    });
    document.getElementById('btn-audit-next').addEventListener('click', () => {
        loadAuditLogs(currentAuditPage + 1);
    });

    if (document.getElementById('btn-export-pdf')) {
        document.getElementById('btn-export-pdf').addEventListener('click', generateReportPdf);
    }
    if (document.getElementById('btn-export-csv')) {
        document.getElementById('btn-export-csv').addEventListener('click', generateReportCsv);
    }
    if (document.getElementById('btn-export-excel')) {
        document.getElementById('btn-export-excel').addEventListener('click', generateReportExcel);
    }

    const closeEditStudent = document.getElementById('close-edit-student');
    if (closeEditStudent) closeEditStudent.addEventListener('click', () => {
        document.getElementById('edit-student-modal').classList.add('hidden');
        document.getElementById('edit-student-modal').style.display = 'none';
    });
    const closeEditTeacher = document.getElementById('close-edit-teacher');
    if (closeEditTeacher) closeEditTeacher.addEventListener('click', () => {
        document.getElementById('edit-teacher-modal').classList.add('hidden');
        document.getElementById('edit-teacher-modal').style.display = 'none';
    });

    document.getElementById('edit-student-form')?.addEventListener('submit', handleEditStudentSubmit);
    document.getElementById('edit-teacher-form')?.addEventListener('submit', handleEditTeacherSubmit);

    const closeDeleteStudent = document.getElementById('close-delete-student');
    if (closeDeleteStudent) closeDeleteStudent.addEventListener('click', () => {
        document.getElementById('delete-student-modal').classList.add('hidden');
        document.getElementById('delete-student-modal').style.display = 'none';
    });
    const cancelDeleteStudent = document.getElementById('btn-cancel-delete-s');
    if (cancelDeleteStudent) cancelDeleteStudent.addEventListener('click', () => {
        document.getElementById('delete-student-modal').classList.add('hidden');
        document.getElementById('delete-student-modal').style.display = 'none';
    });

    const closeDeleteTeacher = document.getElementById('close-delete-teacher');
    if (closeDeleteTeacher) closeDeleteTeacher.addEventListener('click', () => {
        document.getElementById('delete-teacher-modal').classList.add('hidden');
        document.getElementById('delete-teacher-modal').style.display = 'none';
    });
    const cancelDeleteTeacher = document.getElementById('btn-cancel-delete-t');
    if (cancelDeleteTeacher) cancelDeleteTeacher.addEventListener('click', () => {
        document.getElementById('delete-teacher-modal').classList.add('hidden');
        document.getElementById('delete-teacher-modal').style.display = 'none';
    });

    const closeManageRole = document.getElementById('close-manage-role');
    if (closeManageRole) closeManageRole.addEventListener('click', () => {
        document.getElementById('manage-role-modal').classList.add('hidden');
        document.getElementById('manage-role-modal').style.display = 'none';
    });

    const manageRoleForm = document.getElementById('manage-role-form');
    if (manageRoleForm) manageRoleForm.addEventListener('submit', handleManageRoleSubmit);

    const closeErrorModal = document.getElementById('close-error-modal');
    if (closeErrorModal) closeErrorModal.addEventListener('click', () => {
        document.getElementById('error-modal').classList.add('hidden');
        document.getElementById('error-modal').style.display = 'none';
    });
    const okErrorModal = document.getElementById('btn-ok-error');
    if (okErrorModal) okErrorModal.addEventListener('click', () => {
        document.getElementById('error-modal').classList.add('hidden');
        document.getElementById('error-modal').style.display = 'none';
    });

    document.getElementById('departments-filter')?.addEventListener('change', () => {
        const loader = routeLoaders['#manage-departments'];
        if (loader) loader();
    });
    document.getElementById('batches-filter')?.addEventListener('change', () => {
        const loader = routeLoaders['#manage-batches'];
        if (loader) loader();
    });
    document.getElementById('students-filter')?.addEventListener('change', loadStudentsCRUD);
    document.getElementById('students-dept-filter')?.addEventListener('change', loadStudentsCRUD);
    document.getElementById('students-batch-filter')?.addEventListener('change', loadStudentsCRUD);
    document.getElementById('students-year-filter')?.addEventListener('change', loadStudentsCRUD);
    document.getElementById('students-search')?.addEventListener('input', () => {
        clearTimeout(window.studentSearchTimeout);
        window.studentSearchTimeout = setTimeout(loadStudentsCRUD, 300);
    });

    document.getElementById('teachers-filter')?.addEventListener('change', loadTeachersCRUD);
    document.getElementById('teachers-access-filter')?.addEventListener('change', loadTeachersCRUD);
    document.getElementById('teachers-search')?.addEventListener('input', () => {
        clearTimeout(window.teacherSearchTimeout);
        window.teacherSearchTimeout = setTimeout(loadTeachersCRUD, 300);
    });

    setupPasswordToggle('toggle-t-password', 't-password');
    setupPasswordToggle('toggle-edit-t-password', 'edit-t-password');
    document.getElementById('s-dept')?.addEventListener('change', (e) => updateStudentBatchDropdown(e.target.value));

    const debounceLoadAnalytics = () => {
        clearTimeout(window.analyticsFilterTimeout);
        window.analyticsFilterTimeout = setTimeout(loadAnalyticsCenter, 300);
    };

    document.getElementById('analytics-date-range')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-year-filter')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-dept-filter')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-batch-filter')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-start-date')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-end-date')?.addEventListener('change', debounceLoadAnalytics);
    document.getElementById('analytics-session-filter')?.addEventListener('change', debounceLoadAnalytics);

    document.getElementById('s-type')?.addEventListener('change', (e) => {
        const eveningGroup = document.getElementById('s-evening-group');
        if (eveningGroup) {
            eveningGroup.style.display = e.target.value === 'HOSTEL' ? 'block' : 'none';
        }
    });

    document.getElementById('edit-s-type')?.addEventListener('change', (e) => {
        const eveningGroup = document.getElementById('edit-s-evening-group');
        if (eveningGroup) {
            eveningGroup.style.display = e.target.value === 'HOSTEL' ? 'block' : 'none';
        }
    });

    const todayStr = new Date().toISOString().split('T')[0];
    document.getElementById('unlock-date').value = todayStr;
}

function getAnalyticsQueryString() {
    const range = document.getElementById('analytics-date-range')?.value || 'TODAY';
    const year = document.getElementById('analytics-year-filter')?.value || '';
    const deptId = document.getElementById('analytics-dept-filter')?.value || '';
    const batchId = document.getElementById('analytics-batch-filter')?.value || '';
    const session = document.getElementById('analytics-session-filter')?.value || '';

    let startDate = '';
    let endDate = '';
    const today = new Date();

    if (range === 'TODAY') {
        startDate = today.toISOString().split('T')[0];
        endDate = startDate;
    } else if (range === 'THIS_WEEK') {
        const firstDay = new Date(today.setDate(today.getDate() - today.getDay()));
        const lastDay = new Date(today.setDate(today.getDate() - today.getDay() + 6));
        startDate = firstDay.toISOString().split('T')[0];
        endDate = lastDay.toISOString().split('T')[0];
    } else if (range === 'THIS_MONTH') {
        const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
        const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
        startDate = firstDay.toISOString().split('T')[0];
        endDate = lastDay.toISOString().split('T')[0];
    } else if (range === 'LAST_MONTH') {
        const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        const lastDay = new Date(today.getFullYear(), today.getMonth(), 0);
        startDate = firstDay.toISOString().split('T')[0];
        endDate = lastDay.toISOString().split('T')[0];
    } else if (range === 'CUSTOM') {
        startDate = document.getElementById('analytics-start-date')?.value || '';
        endDate = document.getElementById('analytics-end-date')?.value || '';
    } 

    let query = '?';
    if (startDate) query += `startDate=${escapeHTML(startDate)}&`;
    if (endDate) query += `endDate=${escapeHTML(endDate)}&`;
    if (year) query += `year=${escapeHTML(year)}&`;
    if (deptId) query += `departmentId=${deptId}&`;
    if (batchId) query += `batchId=${escapeHTML(batchId)}&`;
    if (session) query += `session=${escapeHTML(session)}&`;

    return query.endsWith('&') || query.endsWith('?') ? query.slice(0, -1) : query;
}

async function loadAnalyticsCenter() {
    const queryParams = getAnalyticsQueryString();

    const elPercent = document.getElementById('stats-attendance-percent');
    const elTodayPresent = document.getElementById('stats-today-present');
    const elTodayAbsent = document.getElementById('stats-today-absent');

    if (elPercent) elPercent.innerHTML = '<span class="skeleton-loader" style="width:50px;height:24px;display:inline-block;"></span>';
    if (elTodayPresent) elTodayPresent.innerHTML = '<span class="skeleton-loader" style="width:50px;height:24px;display:inline-block;"></span>';
    if (elTodayAbsent) elTodayAbsent.innerHTML = '<span class="skeleton-loader" style="width:50px;height:24px;display:inline-block;"></span>';

    const deptBodyEl = document.querySelector('#dept-analytics-table tbody');
    if (deptBodyEl) deptBodyEl.innerHTML = `<tr><td colspan="4"><div class="skeleton-loader" style="height: 20px; width: 100%;"></div></td></tr>`;
    const batchBodyEl = document.querySelector('#batch-analytics-table tbody');
    if (batchBodyEl) batchBodyEl.innerHTML = `<tr><td colspan="4"><div class="skeleton-loader" style="height: 20px; width: 100%;"></div></td></tr>`;
    const sessionBodyEl = document.querySelector('#session-analytics-table tbody');
    if (sessionBodyEl) sessionBodyEl.innerHTML = `<tr><td colspan="4"><div class="skeleton-loader" style="height: 20px; width: 100%;"></div></td></tr>`;

    // 1. Overview Cards
    console.log(`[API_START] traceId=analytics-overview-1 URL=/admin/analytics/overview${escapeHTML(queryParams)}`);
    api.get(`/admin/analytics/overview${escapeHTML(queryParams)}`).then(overview => {
        console.log(`[API_SUCCESS] traceId=analytics-overview-1 status=OK`);
        if (overview && overview.data) {
            const d = overview.data;
            const total = d.totalStudents || 0;
            const present = d.todayPresent || 0;
            const absent = d.todayAbsent || 0;
            const pct = total > 0 ? ((present / total) * 100).toFixed(2) : "0.00";

            if (elPercent) elPercent.textContent = `${pct}%`;
            if (elTodayPresent) elTodayPresent.textContent = `${present} / ${total}`;
            if (elTodayAbsent) elTodayAbsent.textContent = `${absent} / ${total}`;
            renderPieChart(present, absent);
        } else {
            throw new Error("Invalid format");
        }
        console.log(`[API_SETTLED] traceId=analytics-overview-1`);
    }).catch(e => {
        console.log(`[API_FAILURE] traceId=analytics-overview-1 error=${escapeHTML(e.message)}`);
        console.error("Failed to load overview: ", e);
        if (elPercent) elPercent.textContent = '-';
        if (elTodayPresent) elTodayPresent.textContent = '-';
        if (elTodayAbsent) elTodayAbsent.textContent = '-';
        console.log(`[API_SETTLED] traceId=analytics-overview-1`);
    });

    // 2. Department Table
    console.log(`[API_START] traceId=department-analytics-1 URL=/admin/analytics/department${escapeHTML(queryParams)}`);
    api.get(`/admin/analytics/department${escapeHTML(queryParams)}`).then(depts => {
        console.log(`[API_SUCCESS] traceId=department-analytics-1 status=OK`);
        const deptBody = document.querySelector('#dept-analytics-table tbody');
        if (depts && depts.data && deptBody) {
            if (depts.data.length === 0) {
                deptBody.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>No Data</h4><p>No department data available.</p></div></td></tr>`;
            } else {
                deptBody.innerHTML = depts.data.map(item => `
                <tr>
                    <td><strong>${escapeHTML(item.name)}</strong></td>
                    <td>${item.totalStudents ?? 0}</td>
                    <td class="text-center" style="color: var(--success-color); font-weight:600;">${item.presentCount ?? 0}</td>
                    <td class="text-center" style="color: var(--error-color);">${item.absentCount ?? 0}</td>
                </tr>
            `).join('');
            }
            renderBarChart(depts.data);
        }
        console.log(`[API_SETTLED] traceId=department-analytics-1`);
    }).catch(e => {
        console.log(`[API_FAILURE] traceId=department-analytics-1 error=${escapeHTML(e.message)}`);
        const deptBody = document.querySelector('#dept-analytics-table tbody');
        if (deptBody) deptBody.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>Error</h4><p>Failed to load department analytics.</p></div></td></tr>`;
        console.log(`[API_SETTLED] traceId=department-analytics-1`);
    });

    // 3. Batch Table
    console.log(`[API_START] traceId=batch-analytics-1 URL=/admin/analytics/batch${escapeHTML(queryParams)}`);
    api.get(`/admin/analytics/batch${escapeHTML(queryParams)}`).then(batches => {
        console.log(`[API_SUCCESS] traceId=batch-analytics-1 status=OK`);
        const batchBody = document.querySelector('#batch-analytics-table tbody');
        if (batches && batches.data && batchBody) {
            if (batches.data.length === 0) {
                batchBody.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>No Data</h4><p>No batch data available for this report.</p></div></td></tr>`;
            } else {
                batchBody.innerHTML = batches.data.map(item => `
                    <tr>
                        <td><strong>${escapeHTML(item.name)}</strong></td>
                        <td>${item.totalStudents ?? 0}</td>
                        <td class="text-center" style="color: var(--success-color); font-weight:600;">${item.presentCount ?? 0}</td>
                        <td class="text-center" style="color: var(--error-color);">${item.absentCount ?? 0}</td>
                    </tr>
                `).join('');
            }
        }
        console.log(`[API_SETTLED] traceId=batch-analytics-1`);
    }).catch(e => {
        console.log(`[API_FAILURE] traceId=batch-analytics-1 error=${escapeHTML(e.message)}`);
        const batchBody = document.querySelector('#batch-analytics-table tbody');
        if (batchBody) batchBody.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>Error</h4><p>Failed to load batch data.</p></div></td></tr>`;
        console.log(`[API_SETTLED] traceId=batch-analytics-1`);
    });

    // 4. Session Table
    console.log(`[API_START] traceId=session-analytics-1 URL=/admin/analytics/daily${escapeHTML(queryParams)}`);
    api.get(`/admin/analytics/daily${escapeHTML(queryParams)}`).then(sessions => {
        console.log(`[API_SUCCESS] traceId=session-analytics-1 status=OK`);
        const sessionBody = document.querySelector('#session-analytics-table tbody');
        if (sessions && sessions.data && sessionBody) {
            if (sessions.data.length === 0) {
                sessionBody.innerHTML = `<tr><td colspan="3"><div class="empty-state"><h4>No attendance sessions found.</h4></div></td></tr>`;
            } else {
                sessionBody.innerHTML = sessions.data.map(item => `
                <tr>
                    <td><strong>${escapeHTML(item.sessionName)}</strong></td>
                    <td>${item.presentCount ?? 0}</td>
                    <td>${item.absentCount ?? 0}</td>
                </tr>
            `).join('');
            }
        }
        console.log(`[API_SETTLED] traceId=session-analytics-1`);
    }).catch(e => {
        console.log(`[API_FAILURE] traceId=session-analytics-1 error=${escapeHTML(e.message)}`);
        const sessionBody = document.querySelector('#session-analytics-table tbody');
        if (sessionBody) sessionBody.innerHTML = `<tr><td colspan="3"><div class="empty-state"><h4>Error</h4><p>Failed to load session data.</p></div></td></tr>`;
        console.log(`[API_SETTLED] traceId=session-analytics-1`);
    });

    // 5. Weekly Trend Chart
    const weeklyQueryParams = queryParams ? queryParams.replace(/&?startDate=[^&]*/g, '').replace(/&?endDate=[^&]*/g, '').replace(/^\?&/, '?') : '';
    api.get(`/admin/analytics/weekly${escapeHTML(weeklyQueryParams)}`).then(trend => {
        if (trend && trend.data) {
            renderTrendChart(trend.data);
        }
    }).catch(e => {
        console.error("Failed to load trend chart: ", e);
    });
}

function renderTrendChart(data) {
    const ctx = document.getElementById('weeklyTrendChart').getContext('2d');
    if (trendChart) {
        trendChart.destroy();
    }

    if (!data || data.length === 0) {
        trendChart = null;
        return;
    }

    const labels = data.map(item => `${item.date} (${item.dayOfWeek.substring(0, 3)})`);
    const presentData = data.map(item => item.presentCount ?? 0);
    const absentData = data.map(item => item.absentCount ?? 0);

    trendChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Present',
                    data: presentData,
                    borderColor: '#2ecc71',
                    backgroundColor: 'rgba(46, 204, 113, 0.08)',
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true
                },
                {
                    label: 'Absent',
                    data: absentData,
                    borderColor: '#e74c3c',
                    backgroundColor: 'rgba(231, 76, 60, 0.08)',
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        stepSize: 1
                    }
                }
            }
        }
    });
}

async function loadMasterData() {
    try {
        const [deptsRes, batchesRes, teachersRes] = await Promise.all([
            api.get('/admin/departments').catch(() => []),
            api.get('/admin/batches').catch(() => []),
            api.get('/admin/teachers').catch(() => [])
        ]);
        const depts = extractArray(deptsRes);
        const batches = extractArray(batchesRes);
        const teachers = extractArray(teachersRes);

        const activeDepts = depts.filter(d => d.active);
        const activeBatches = batches.filter(b => b.active);
        const activeTeachers = teachers.filter(t => t.active);
        window._allActiveBatches = activeBatches;

        populateSelect(document.getElementById('s-dept'), activeDepts, { placeholder: "Select Department" });
        populateSelect(document.getElementById('t-dept'), activeDepts, { placeholder: "Select Department" });
        populateSelect(document.getElementById('edit-t-department'), activeDepts, { placeholder: "Select Department" });
        populateSelect(document.getElementById('edit-s-dept'), activeDepts, { placeholder: "Select Department" });
        populateSelect(document.getElementById('students-dept-filter'), activeDepts, { placeholder: "All Departments" });
        populateSelect(document.getElementById('analytics-dept-filter'), activeDepts, { placeholder: "All Departments" });
        populateSelect(document.getElementById('export-dept'), activeDepts, { placeholder: "All Departments" });
        populateSelect(document.getElementById('search-dept'), activeDepts, { placeholder: "All Departments" });

        populateSelect(document.getElementById('s-batch'), activeBatches, { placeholder: "Select Batch" });
        populateSelect(document.getElementById('edit-s-batch'), activeBatches, { placeholder: "Select Batch" });
        populateSelect(document.getElementById('students-batch-filter'), activeBatches, { placeholder: "All Batches" });
        populateSelect(document.getElementById('analytics-batch-filter'), activeBatches, { placeholder: "All Batches" });
        populateSelect(document.getElementById('export-batch'), activeBatches, { placeholder: "All Batches" });
        populateSelect(document.getElementById('search-batch'), activeBatches, { placeholder: "All Batches" });

        populateSelect(document.getElementById('unlock-teacher-select'), activeTeachers, { placeholder: "All Teachers (Full Dept)" });

    } catch (e) {
        console.error("Could not fetch master data (departments/batches/teachers): ", e);
    }
}

function updateStudentBatchDropdown(deptId) {
    const select = document.getElementById('s-batch');
    if (!select) return;
    const activeBatches = window._allActiveBatches || [];
    if (!deptId) {
        populateSelect(select, activeBatches, { placeholder: "Select Batch" });
        return;
    }
    const filtered = activeBatches.filter(b => b.departmentId == deptId || (b.department && b.department.id == deptId));
    if (filtered.length > 0) {
        populateSelect(select, filtered, { placeholder: "Select Batch" });
    } else {
        populateSelect(select, activeBatches, { placeholder: "Select Batch" });
    }
}

function setupPasswordToggle(toggleBtnId, inputId) {
    const btn = document.getElementById(toggleBtnId);
    const input = document.getElementById(inputId);
    if (!btn || !input) return;
    btn.addEventListener('click', (e) => {
        e.preventDefault();
        const isPassword = input.type === 'password';
        input.type = isPassword ? 'text' : 'password';
        btn.innerHTML = isPassword
            ? `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" style="width: 20px; height: 20px;">
                <path fill-rule="evenodd" d="M3.28 2.22a.75.75 0 0 0-1.06 1.06l14.5 14.5a.75.75 0 1 0 1.06-1.06l-1.745-1.745a10.029 10.029 0 0 0 3.3-4.385 1.65 1.65 0 0 0 0-1.185A10.004 10.004 0 0 0 9.999 3a9.956 9.956 0 0 0-4.744 1.194L3.28 2.22ZM7.752 6.692a3.992 3.992 0 0 1 4.556 4.556l-4.556-4.556Z" clip-rule="evenodd" />
                <path d="M10.748 13.93 7.07 10.252a4 4 0 0 0 3.678 3.678Z" />
               </svg>`
            : `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" style="width: 20px; height: 20px;">
                <path d="M10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" />
                <path fill-rule="evenodd" d="M.664 10.59a1.651 1.651 0 0 1 0-1.186A10.004 10.004 0 0 1 10 3c4.257 0 7.893 2.66 9.336 6.41.147.381.146.804 0 1.186A10.004 10.004 0 0 1 10 17c-4.257 0-7.893-2.66-9.336-6.41ZM14 10a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" clip-rule="evenodd" />
               </svg>`;
    });
}

function renderDepartmentsCRUD(data) {
    const body = document.querySelector('#departments-crud-table tbody');
    if (!body) return;

    const filterVal = document.getElementById('departments-filter').value;
    let filteredData = data;
    if (filterVal === 'ACTIVE') filteredData = data.filter(d => d.active);
    if (filterVal === 'ARCHIVED') filteredData = data.filter(d => !d.active);

    if (filteredData.length === 0) {
        body.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>No departments configured.</h4></div></td></tr>`;
        return;
    }

    body.innerHTML = filteredData.map((d, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>
                <span id="dept-name-${d.id}">${escapeHTML(d.name)}</span>
                <input type="text" id="dept-input-${d.id}" class="form-control hidden" value="${escapeHTML(d.name)}" style="width: 150px; display:inline-block; padding: 4px 8px;">
            </td>
            <td>
                <span id="dept-code-${d.id}">${d.departmentCode || '<span style="color:var(--error-color);font-size:11px;">Missing Code</span>'}</span>
                <input type="text" id="dept-code-input-${d.id}" class="form-control hidden" value="${d.departmentCode || ''}" style="width: 80px; display:inline-block; padding: 4px 8px;">
            </td>
            <td>
                <span style="color: ${d.active ? 'var(--success-color)' : 'var(--error-color)'}; font-weight:600;">
                    ${d.active ? 'Active' : 'Archived'}
                </span>
            </td>
            <td>
                ${d.active ? `
                    <button class="btn btn-primary" style="padding: 4px 8px; font-size:13px;" onclick="toggleEditDept(${d.id})" id="dept-edit-btn-${d.id}">Rename</button>
                    <button class="btn btn-primary hidden" style="padding: 4px 8px; font-size:13px;" onclick="saveDept(${d.id})" id="dept-save-btn-${d.id}">Save</button>
                    <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--error-color); color:white; margin-left: 5px;" onclick="if(confirm('Archive this department?')) toggleDeptStatus(${d.id}, false)">Archive</button>
                ` : `
                    <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--success-color); color:white;" onclick="if(confirm('Activate this department?')) toggleDeptStatus(${d.id}, true)">Activate</button>
                `}
                <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: #991b1b; color:white; margin-left: 5px;" onclick="if(prompt('Type DELETE to permanently remove this record.') === 'DELETE') deleteDepartment(${d.id})">Perm Delete</button>
            </td>
        </tr>
    `).join('');
}

window.toggleEditDept = (id) => {
    document.getElementById(`dept-name-${escapeHTML(id)}`).classList.add('hidden');
    document.getElementById(`dept-input-${escapeHTML(id)}`).classList.remove('hidden');
    document.getElementById(`dept-code-${escapeHTML(id)}`).classList.add('hidden');
    document.getElementById(`dept-code-input-${escapeHTML(id)}`).classList.remove('hidden');
    document.getElementById(`dept-edit-btn-${escapeHTML(id)}`).classList.add('hidden');
    document.getElementById(`dept-save-btn-${escapeHTML(id)}`).classList.remove('hidden');
};

window.saveDept = async (id) => {
    const newName = document.getElementById(`dept-input-${escapeHTML(id)}`).value.trim();
    const newCode = document.getElementById(`dept-code-input-${escapeHTML(id)}`).value.trim();
    try {
        await api.put(`/admin/departments/${escapeHTML(id)}/rename`, { name: newName, departmentCode: newCode });
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to update department", "error");
    }
};

window.toggleDeptStatus = async (id, status) => {
    try {
        await api.put(`/admin/departments/${escapeHTML(id)}/toggle`, { active: status });
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to toggle status", "error");
    }
};

window.deleteDepartment = async (id) => {
    try {
        await api.deleteRequest(`/admin/departments/${escapeHTML(id)}`);
        showToast("Department permanently deleted", "success");
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to delete department", "error");
    }
};

function renderBatchesCRUD(data) {
    const body = document.querySelector('#batches-crud-table tbody');
    if (!body) return;

    const filterVal = document.getElementById('batches-filter').value;
    let filteredData = data;
    if (filterVal === 'ACTIVE') filteredData = data.filter(b => b.active);
    if (filterVal === 'ARCHIVED') filteredData = data.filter(b => !b.active);

    if (filteredData.length === 0) {
        body.innerHTML = `<tr><td colspan="4"><div class="empty-state"><h4>No batches configured.</h4></div></td></tr>`;
        return;
    }

    body.innerHTML = filteredData.map((b, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>
                <span id="batch-name-${b.id}">${escapeHTML(b.name)}</span>
                <input type="text" id="batch-input-${b.id}" class="form-control hidden" value="${escapeHTML(b.name)}" style="width: 200px; display:inline-block; padding: 4px 8px;">
            </td>
            <td>
                <span style="color: ${b.active ? 'var(--success-color)' : 'var(--error-color)'}; font-weight:600;">
                    ${b.active ? 'Active' : 'Archived'}
                </span>
            </td>
            <td>
                ${b.active ? `
                    <button class="btn btn-primary" style="padding: 4px 8px; font-size:13px;" onclick="toggleEditBatch(${b.id})" id="batch-edit-btn-${b.id}">Rename</button>
                    <button class="btn btn-primary hidden" style="padding: 4px 8px; font-size:13px;" onclick="saveBatch(${b.id})" id="batch-save-btn-${b.id}">Save</button>
                    <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--error-color); color:white; margin-left: 5px;" onclick="if(confirm('Archive this batch?')) toggleBatchStatus(${b.id}, false)">Archive</button>
                ` : `
                    <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--success-color); color:white;" onclick="if(confirm('Activate this batch?')) toggleBatchStatus(${b.id}, true)">Activate</button>
                `}
                <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: #991b1b; color:white; margin-left: 5px;" onclick="if(prompt('Type DELETE to permanently remove this record.') === 'DELETE') deleteBatch(${b.id})">Perm Delete</button>
            </td>
        </tr>
    `).join('');
}

window.toggleEditBatch = (id) => {
    document.getElementById(`batch-name-${escapeHTML(id)}`).classList.add('hidden');
    document.getElementById(`batch-input-${escapeHTML(id)}`).classList.remove('hidden');
    document.getElementById(`batch-edit-btn-${escapeHTML(id)}`).classList.add('hidden');
    document.getElementById(`batch-save-btn-${escapeHTML(id)}`).classList.remove('hidden');
};

window.saveBatch = async (id) => {
    const newName = document.getElementById(`batch-input-${escapeHTML(id)}`).value.trim();
    try {
        await api.put(`/admin/batches/${escapeHTML(id)}/rename`, { name: newName });
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to rename batch", "error");
    }
};

window.toggleBatchStatus = async (id, status) => {
    try {
        await api.put(`/admin/batches/${escapeHTML(id)}/toggle`, { active: status });
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to toggle batch status", "error");
    }
};

window.deleteBatch = async (id) => {
    try {
        await api.deleteRequest(`/admin/batches/${escapeHTML(id)}`);
        showToast("Batch permanently deleted", "success");
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || "Failed to delete batch", "error");
    }
};

async function loadStudentsCRUD() {
    try {
        const deptFilter = document.getElementById('students-dept-filter')?.value || '';
        const batchFilter = document.getElementById('students-batch-filter')?.value || '';
        const yearFilter = document.getElementById('students-year-filter')?.value || '';
        const filterVal = document.getElementById('students-filter')?.value || 'ALL';
        const searchVal = document.getElementById('students-search')?.value || '';

        let queryParams = new URLSearchParams();
        if (deptFilter) queryParams.append("departmentId", deptFilter);
        if (batchFilter) queryParams.append("batchId", batchFilter);
        if (yearFilter) queryParams.append("academicYear", yearFilter);
        if (searchVal) queryParams.append("searchQuery", searchVal);

        const res = await api.get(`/admin/students?${queryParams.toString()}`);
        const body = document.querySelector('#students-crud-table tbody');
        if (!body) return;
        if (res && res.success && res.data) {
            let filteredData = res.data;
            if (filterVal === 'ACTIVE') filteredData = filteredData.filter(s => s.active !== false); 
            if (filterVal === 'ARCHIVED') filteredData = filteredData.filter(s => s.active === false);

            const students = filteredData;
            if (students.length === 0) {
                body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><h4>No students found.</h4></div></td></tr>`;
                document.getElementById('students-pagination-controls').classList.add('hidden');
                return;
            }

            body.innerHTML = filteredData.map((s, index) => `
                <tr>
                    <td>${index + 1}</td>
                    <td>${escapeHTML(s.name)} <br> <small style="color:${s.active !== false ? 'var(--success-color)' : 'var(--error-color)'}">${s.active !== false ? 'Active' : 'Archived'}</small></td>
                    <td>${escapeHTML(s.registerNo)}</td>
                    <td>${escapeHTML(s.departmentName)}</td>
                    <td>${escapeHTML(s.batchName)}</td>
                    <td>
                        ${s.active !== false ? `
                            <button class="btn btn-primary" style="padding: 4px 8px; font-size:13px; margin-right: 5px;" onclick='openEditStudentModal(${JSON.stringify(s).replace(/'/g, "&apos;")})'>Edit</button>
                            <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--error-color); color:white;" onclick='openDeleteStudentModal(${s.id}, "${s.name.replace(/"/g, '&quot;')}")'>Archive</button>
                        ` : `
                            <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--success-color); color:white;" onclick="restoreStudent(${s.id})">Activate</button>
                        `}
                    </td>
                </tr>
            `).join('');
        }
    } catch(e) { 
        console.error(e); 
        const body = document.querySelector('#students-crud-table tbody');
        if (body) body.innerHTML = `<tr><td colspan="6"><div class="empty-state"><h4>Error</h4><p>Failed to load students.</p></div></td></tr>`;
    }
}

async function loadTeachersCRUD() {
    try {
        const filterVal = document.getElementById('teachers-filter')?.value || 'ALL';
        const searchVal = document.getElementById('teachers-search')?.value || '';
        const accessVal = document.getElementById('teachers-access-filter')?.value || '';

        let queryParams = new URLSearchParams();
        if (searchVal) queryParams.append("searchQuery", searchVal);

        const res = await api.get(`/admin/teachers?${queryParams.toString()}`);
        const body = document.querySelector('#teachers-crud-table tbody');
        if (!body) return;
        if (res && res.success && res.data) {
            let filteredData = res.data;
            if (filterVal === 'ACTIVE') filteredData = filteredData.filter(t => t.active !== false);
            if (filterVal === 'ARCHIVED') filteredData = filteredData.filter(t => t.active === false);
            // Re-apply accessFilter on client-side if needed or just drop it if API doesn't support it
            const accessFilter = document.getElementById('teachers-access-filter')?.value || '';
            if (accessFilter) {
                // If API doesn't return role yet, we might not be able to filter by access type, but keep logic in case
                filteredData = filteredData.filter(t => t.accessType == accessFilter || (t.user && t.user.role == accessFilter));
            }

            const teachers = filteredData;
            if (teachers.length === 0) {
                body.innerHTML = `<tr><td colspan="5"><div class="empty-state"><h4>No teachers found.</h4></div></td></tr>`;
                return;
            }

            body.innerHTML = filteredData.map((t, index) => {
                const name = t.name || 'N/A';
                const active = t.active !== false;
                const role = t.accessType || (t.user && t.user.role) || 'TEACHER';
                return `
                <tr>
                    <td>${index + 1}</td>
                    <td>${escapeHTML(name)} <br> <small style="color:${active ? 'var(--success-color)' : 'var(--error-color)'}">${active ? 'Active' : 'Archived'}</small></td>
                    <td>${escapeHTML(t.employeeId)}</td>
                    <td>${escapeHTML(role)}</td>
                    <td>
                        ${active ? `
                            <button class="btn btn-primary" style="padding: 4px 8px; font-size:13px; margin-right: 5px;" onclick='openEditTeacherModal(${JSON.stringify(t).replace(/'/g, "&apos;")})'>Edit</button>
                            ${getCurrentUser().role === 'ADMIN' ? `<button class="btn" style="padding: 4px 8px; font-size:13px; margin-right: 5px; background-color: var(--secondary-color); color: white;" onclick="openManageRoleModal('${t.userId || t.email}', '${escapeHTML(role)}')">Role</button>` : ''}
                            <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--error-color); color:white;" onclick='openDeleteTeacherModal(${t.id}, "${name.replace(/"/g, '&quot;')}")'>Archive</button>
                        ` : `
                            <button class="btn" style="padding: 4px 8px; font-size:13px; background-color: var(--success-color); color:white;" onclick="restoreTeacher(${t.id})">Activate</button>
                        `}
                    </td>
                </tr>
            `}).join('');
        }
    } catch(e) { 
        console.error(e); 
        const body = document.querySelector('#teachers-crud-table tbody');
        if (body) body.innerHTML = `<tr><td colspan="5"><div class="empty-state"><h4>Error</h4><p>Failed to load teachers.</p></div></td></tr>`;
    }
}

window.openEditStudentModal = (studentData) => {
    document.getElementById('edit-s-id').value = studentData.id || '';
    document.getElementById('edit-s-name').value = studentData.name || '';
    const deptSelect = document.getElementById('edit-s-dept');
    if (deptSelect && studentData.departmentId) {
        deptSelect.innerHTML = document.getElementById('s-dept').innerHTML; // copy options
        deptSelect.value = studentData.departmentId;
    }
    document.getElementById('edit-s-year').value = studentData.academicYear || studentData.year || '';
    const batchSelect = document.getElementById('edit-s-batch');
    if (batchSelect && studentData.batchId) {
        batchSelect.innerHTML = document.getElementById('s-batch').innerHTML;
        batchSelect.value = studentData.batchId;
    }
    const typeSelect = document.getElementById('edit-s-type');
    if (typeSelect && studentData.type) {
        typeSelect.value = studentData.type;
        const eveningGroup = document.getElementById('edit-s-evening-group');
        if (eveningGroup) {
            eveningGroup.style.display = studentData.type === 'HOSTEL' ? 'block' : 'none';
        }
    }
    const eveningSelect = document.getElementById('edit-s-evening');
    if (eveningSelect) {
        eveningSelect.value = studentData.eveningClassEnabled ? 'YES' : 'NO';
    }

    const modal = document.getElementById('edit-student-modal');
    modal.classList.remove('hidden');
    modal.style.display = 'flex';
};

window.openEditTeacherModal = (teacherData) => {
    document.getElementById('edit-t-id').value = teacherData.id || '';
    document.getElementById('edit-t-name').value = teacherData.name || '';
    document.getElementById('edit-t-email').value = teacherData.email || '';
    document.getElementById('edit-t-empid').value = teacherData.employeeId || '';
    const accessSelect = document.getElementById('edit-t-access-type');
    if (accessSelect) {
        accessSelect.value = teacherData.accessType || (teacherData.user && teacherData.user.role) || 'TEACHER';
    }
    const deptId = (teacherData.department && teacherData.department.id) || teacherData.departmentId || '';
    const editDeptSelect = document.getElementById('edit-t-department');
    if (editDeptSelect) editDeptSelect.value = deptId;
    document.getElementById('edit-t-status').value = teacherData.active !== false ? 'true' : 'false';
    document.getElementById('edit-t-created').textContent = teacherData.createdAt || 'N/A';
    document.getElementById('edit-t-updated').textContent = teacherData.updatedAt || 'N/A';
    document.getElementById('edit-t-lastlogin').textContent = (teacherData.user && teacherData.user.lastLogin) || 'N/A';

    document.getElementById('edit-t-password').value = '';

    const modal = document.getElementById('edit-teacher-modal');
    modal.classList.remove('hidden');
    modal.style.display = 'flex';
};

async function handleEditStudentSubmit(event) {
    event.preventDefault();
    const id = document.getElementById('edit-s-id').value;
    const payload = {
        name: document.getElementById('edit-s-name').value.trim(),
        departmentId: parseInt(document.getElementById('edit-s-dept').value, 10) || null,
        batchId: parseInt(document.getElementById('edit-s-batch').value, 10) || null,
        year: parseInt(document.getElementById('edit-s-year').value, 10),
        hosteller: document.getElementById('edit-s-type').value === 'HOSTEL',
        eveningClassEnabled: document.getElementById('edit-s-evening')?.value === 'YES',
        active: true
    };

    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    try {
        await api.put(`/admin/students/${escapeHTML(id)}`, payload);
        showToast('Student updated successfully', 'success');
        const modal = document.getElementById('edit-student-modal');
        modal.classList.add('hidden');
        modal.style.display = 'none';
        loadStudentsCRUD();
        loadAnalyticsCenter();
    } catch(e) {
        showToast(e.message || "Failed to update student", "error");
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleEditTeacherSubmit(event) {
    event.preventDefault();
    const id = document.getElementById('edit-t-id').value;
    // Password validation if provided
    const pwd = document.getElementById('edit-t-password').value;
    if (pwd && pwd.length > 0) {
        const strongRegex = new RegExp("^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\\$%\\^&\\*])(?=.{12,})");
        if (!strongRegex.test(pwd)) {
            showToast("New password must be at least 12 characters and include uppercase, lowercase, number, and special character.", "error");
            return;
        }
    }

    const editDeptValue = document.getElementById('edit-t-department') ? document.getElementById('edit-t-department').value : '';
    const payload = {
        name: document.getElementById('edit-t-name').value.trim(),
        email: document.getElementById('edit-t-email').value.trim(),
        employeeId: document.getElementById('edit-t-empid').value.trim(),
        accessType: document.getElementById('edit-t-access-type') ? document.getElementById('edit-t-access-type').value : 'TEACHER',
        departmentId: editDeptValue ? parseInt(editDeptValue, 10) : null,
        active: document.getElementById('edit-t-status').value === 'true',
        newPassword: pwd
    };

    if (!pwd || pwd.trim() === "") {
        delete payload.newPassword;
    }

    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    try {
        await api.put(`/admin/teachers/${escapeHTML(id)}`, payload);
        showToast('Teacher updated successfully', 'success');
        const modal = document.getElementById('edit-teacher-modal');
        modal.classList.add('hidden');
        modal.style.display = 'none';
        loadTeachersCRUD();
        loadAnalyticsCenter();
    } catch(e) {
        showToast(e.message || "Failed to update teacher", "error");
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleManageRoleSubmit(e) {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    try {
        if(btn) {
            btn.disabled = true;
            btn.textContent = 'Updating...';
        }
        const userId = document.getElementById('manage-role-userId').value;
        const newRole = document.getElementById('manage-role-select').value;
        const payload = {
            userId: userId,
            role: newRole
        };
        const res = await api.post('/admin/roles/assign', payload);
        if (res && res.success) {
            showToast("Role updated successfully", "success");
            document.getElementById('manage-role-modal').classList.add('hidden');
            document.getElementById('manage-role-modal').style.display = 'none';
            loadTeachersCRUD();
        } else {
            showErrorModal(res.message || "Failed to update role");
        }
    } catch (error) {
        showErrorModal(error.message || "Failed to update role");
    } finally {
        if(btn) {
            btn.disabled = false;
            btn.textContent = 'Update Role';
        }
    }
}

window.openDeleteStudentModal = async (id, name) => {
    const userInput = prompt(`You are about to delete student: ${escapeHTML(name)}\n\nType DELETE to confirm:`);
    if (userInput === 'DELETE') {
        try {
            await api.deleteRequest(`/admin/students/${escapeHTML(id)}`);
            loadStudentsCRUD();
            loadAnalyticsCenter();
            showToast('Student deleted successfully', 'success');
        } catch(e) { showToast(e.message || "Failed to delete student", "error"); }
    } else if (userInput !== null) {
        showToast('Confirmation failed. You must type DELETE exactly.', 'error');
    }
};

window.openDeleteTeacherModal = async (id, name) => {
    const userInput = prompt(`You are about to delete teacher: ${escapeHTML(name)}\n\nType DELETE to confirm:`);
    if (userInput === 'DELETE') {
        try {
            await api.deleteRequest(`/admin/teachers/${escapeHTML(id)}`);
            loadTeachersCRUD();
            loadAnalyticsCenter();
            showToast('Teacher deleted successfully', 'success');
        } catch(e) { showToast(e.message || "Failed to delete teacher", "error"); }
    } else if (userInput !== null) {
        showToast('Confirmation failed. You must type DELETE exactly.', 'error');
    }
};

window.restoreStudent = async (id) => {
    try {
        await api.put(`/admin/students/${escapeHTML(id)}/restore`, {});
        loadStudentsCRUD();
        loadAnalyticsCenter();
        showToast('Student restored successfully', 'success');
    } catch(e) { showToast(e.message || "Failed to restore student", "error"); }
};

window.restoreTeacher = async (id) => {
    try {
        await api.put(`/admin/teachers/${escapeHTML(id)}/restore`, {});
        loadTeachersCRUD();
        loadAnalyticsCenter();
        showToast('Teacher restored successfully', 'success');
    } catch(e) { showToast(e.message || "Failed to restore teacher", "error"); }
};

async function handleCreateStudent(event) {
    event.preventDefault();

    const deptId  = parseInt(document.getElementById('s-dept').value, 10);
    const batchId = parseInt(document.getElementById('s-batch').value, 10);
    const year    = parseInt(document.getElementById('s-year').value, 10);

    if (isNaN(deptId)) {
        showToast('Please select a Department.', 'warning');
        return;
    }
    if (isNaN(batchId)) {
        showToast('Please select a Batch.', 'warning');
        return;
    }
    if (isNaN(year)) {
        showToast('Please enter a valid Year.', 'warning');
        return;
    }

    const payload = {
        name:         document.getElementById('s-name').value.trim(),
        registerNumber:   document.getElementById('s-register-number').value.trim().toUpperCase(),
        departmentId: deptId,
        year:         year,
        hosteller:    document.getElementById('s-type').value === 'HOSTEL',
        eveningClassEnabled: document.getElementById('s-evening').value === 'YES',
        batchId:      batchId,
        active:       true
    };

    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    try {
        await api.post('/admin/students', payload);
        showToast('✓ Student registered successfully', 'success');
        clearForm('student-form');
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || 'Failed to register student', 'error');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleCreateTeacher(event) {
    event.preventDefault();

    const email = document.getElementById('t-userId').value.trim();
    const password = document.getElementById('t-password').value;

    // Validate email
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
        showToast('Please enter a valid email address.', 'warning');
        return;
    }

    // Validate strong password
    const pwdRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{12,}$/;
    if (!pwdRegex.test(password)) {
        showToast('Password must be at least 12 characters, include uppercase, lowercase, number, and special character.', 'warning');
        return;
    }

    const deptValue = document.getElementById('t-dept') ? document.getElementById('t-dept').value : '';
    const payload = {
        name:         document.getElementById('t-name').value.trim(),
        userId:       email,
        employeeId:   document.getElementById('t-emp-id').value.trim().toUpperCase(),
        accessType:   document.getElementById('t-access-type') ? document.getElementById('t-access-type').value : 'TEACHER',
        departmentId: deptValue ? parseInt(deptValue, 10) : null,
        password:     password
    };

    if (!payload.name || !payload.userId || !payload.password) {
        showToast('Please fill all required teacher fields.', 'warning');
        return;
    }

    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    try {
        await api.post('/admin/teachers', payload);
        showToast('✓ Teacher registered successfully', 'success');
        clearForm('teacher-form');
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || 'Failed to register teacher', 'error');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleAddDepartment(e) {
    e.preventDefault();
    const nameInput = document.getElementById('new-dept-name');
    const codeInput = document.getElementById('new-dept-code');
    const msg = document.getElementById('dept-message');
    const submitBtn = e.target.querySelector('button[type="submit"]');
    
    if (submitBtn) submitBtn.disabled = true;
    
    try {
        await api.post('/admin/departments', { name: nameInput.value, departmentCode: codeInput.value });
        nameInput.value = '';
        codeInput.value = '';
        msg.className = 'success-message';
        msg.textContent = 'Department added successfully';
        msg.classList.remove('hidden');
        loadAllDropdownsAndCRUDs();
    } catch(err) {
        msg.className = 'error-message';
        msg.textContent = err.message || 'Failed to add department';
        msg.classList.remove('hidden');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleAddBatch(event) {
    event.preventDefault();
    const nameInput = document.getElementById('new-batch-name');
    const submitBtn = event.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;
    try {
        await api.post('/admin/batches', { name: nameInput.value.trim() });
        showToast('Batch created successfully', 'success');
        nameInput.value = '';
        loadAllDropdownsAndCRUDs();
    } catch (e) {
        showToast(e.message || 'Failed to add batch', 'error');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Session Override & Unlock Form Functions (see the single definition below - a duplicate
// definition of this function used to exist here and silently overwrite the one further down,
// and that surviving version posted a query string that didn't match the backend's
// @RequestBody contract at all, so unlock/lock never actually worked. Consolidated to one.

async function loadLockStatus() {
    const dateEl = document.getElementById('unlock-date');
    const sessionEl = document.getElementById('unlock-session-select');
    if (!dateEl || !sessionEl) return;
    const date = dateEl.value;
    const session = sessionEl.value;
    if (!date || !session) return;
    try {
        const response = await api.get(`/admin/attendance/lock-status?date=${escapeHTML(date)}&session=${escapeHTML(session)}`);
        const data = response.data;
        const badge = document.getElementById('lock-status-badge');
        if (badge) {
            let text = data.unlocked ? 'Unlocked (Allow Marking)' : 'Locked (Default Constraints)';
            if (data.unlocked && data.expiryTime) {
                text += ` [Expires: ${data.expiryTime.replace('T', ' ').substring(0, 19)}]`;
            }
            badge.textContent = text;
            badge.style.color = data.unlocked ? 'var(--success-color)' : 'var(--error-color)';
            document.getElementById('lock-updated-by').textContent = data.updatedBy || 'SYSTEM';
            document.getElementById('lock-updated-time').textContent = data.updatedTime ? data.updatedTime.replace('T', ' ').substring(0, 19) : 'N/A';
        }
    } catch (e) {
        console.error('Failed to fetch lock status', e);
    }
}
window.loadLockStatus = loadLockStatus;

function renderPieChart(present, absent) {
    const canvas = document.getElementById('todayAttendancePieChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (todayPieChart) {
        todayPieChart.destroy();
    }

    if (present === 0 && absent === 0) {
        todayPieChart = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: ['No Data Today'],
                datasets: [{
                    data: [1],
                    backgroundColor: ['#e2e8f0']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false
            }
        });
        return;
    }

    todayPieChart = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: ['Present', 'Absent'],
            datasets: [{
                data: [present, absent],
                backgroundColor: ['#2ecc71', '#e74c3c'],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}

function renderBarChart(data) {
    const canvas = document.getElementById('departmentBarChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (departmentBarChart) {
        departmentBarChart.destroy();
    }

    if (!data || data.length === 0) {
        departmentBarChart = null;
        return;
    }

    const labels = data.map(item => item.name);
    const presentValues = data.map(item => item.presentCount ?? 0);
    const absentValues = data.map(item => item.absentCount ?? 0);

    departmentBarChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Present',
                    data: presentValues,
                    backgroundColor: '#2ecc71',
                    borderColor: '#27ae60',
                    borderWidth: 1
                },
                {
                    label: 'Absent',
                    data: absentValues,
                    backgroundColor: '#e74c3c',
                    borderColor: '#c0392b',
                    borderWidth: 1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        stepSize: 1
                    }
                }
            },
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}

async function loadSearchDropdowns() {
    try {
        const depts = await api.get('/admin/departments');
        const batches = await api.get('/admin/batches');

        const sDept = document.getElementById('search-dept');
        const sBatch = document.getElementById('search-batch');

        if (sDept) {
            sDept.innerHTML = '<option value="">All Departments</option>' + depts.data
                .filter(d => d.active)
                .map(d => `<option value="${d.id}">${escapeHTML(d.name)}</option>`)
                .join('');
        }
        if (sBatch) {
            sBatch.innerHTML = '<option value="">All Batches</option>' + batches.data
                .filter(b => b.active)
                .map(b => `<option value="${b.id}">${escapeHTML(b.name)}</option>`)
                .join('');
        }
    } catch (e) {
        console.error("Could not load search dropdowns: ", e);
    }
}

async function loadAttendanceLogsSearch(page) {
    currentSearchPage = page;
    let params = `page=${escapeHTML(page)}&size=10`;
    if (currentSearchFilters.studentName) params += `&studentName=${encodeURIComponent(currentSearchFilters.studentName)}`;
    if (currentSearchFilters.registerNumber) params += `&registerNumber=${encodeURIComponent(currentSearchFilters.registerNumber)}`;
    if (currentSearchFilters.departmentId) params += `&departmentId=${escapeHTML(currentSearchFilters.departmentId)}`;
    if (currentSearchFilters.batchId) params += `&batchId=${escapeHTML(currentSearchFilters.batchId)}`;
    if (currentSearchFilters.date) params += `&date=${escapeHTML(currentSearchFilters.date)}`;
    if (currentSearchFilters.session) params += `&session=${escapeHTML(currentSearchFilters.session)}`;
    if (currentSearchFilters.status) params += `&status=${escapeHTML(currentSearchFilters.status)}`;
    if (currentSearchFilters.eveningClassEnabled !== undefined && currentSearchFilters.eveningClassEnabled !== '') {
        params += `&eveningClassEnabled=${escapeHTML(currentSearchFilters.eveningClassEnabled)}`;
    }
    const tableBody = document.querySelector('#search-results-table tbody');
    if (tableBody) {
        tableBody.innerHTML = `<tr><td colspan="7" class="text-center">Loading attendance records…</td></tr>`;
    }

    try {
        const response = await api.get(`/admin/attendance/search?${escapeHTML(params)}`);
        const tableBody = document.querySelector('#search-results-table tbody');
        if (response && response.data && tableBody) {
            const dataPage = response.data;
            const logs = dataPage.content || [];

            if (logs.length === 0) {
                tableBody.innerHTML = `<tr><td colspan="7"><div class="empty-state"><h4>No attendance records found.</h4></div></td></tr>`;
                document.getElementById('search-pagination-controls').classList.add('hidden');
            } else {
                tableBody.innerHTML = logs.map((item, index) => {
                    const serialNumber = (page * 10) + index + 1;
                    return `
                    <tr>
                        <td>${escapeHTML(serialNumber)}</td>
                        <td>${escapeHTML(item.registerNumber)}</td>
                        <td>${escapeHTML(item.studentName)}</td>
                        <td>${item.date}</td>
                        <td>${escapeHTML(item.session)}</td>
                        <td style="color: ${item.status === 'PRESENT' ? 'var(--success-color)' : item.status === 'ABSENT' ? 'var(--error-color)' : 'var(--secondary-color)'}; font-weight: 600;">
                            ${escapeHTML(item.status)}
                        </td>
                        <td>${item.markedBy || 'SYSTEM'}</td>
                        <td>
                            <button class="btn btn-secondary" style="padding: 4px 8px; font-size:12px;" onclick='openEditAttendanceModal(${JSON.stringify(item).replace(/'/g, "&apos;")})'>Edit</button>
                        </td>
                    </tr>
                `}).join('');
            }

            const currentPageNum = (dataPage.number !== undefined ? dataPage.number : (dataPage.pageable ? dataPage.pageable.pageNumber : page)) + 1;
            const totalPagesNum = Math.max(dataPage.totalPages !== undefined ? dataPage.totalPages : 1, 1);
            document.getElementById('search-pagination-info').textContent = `Page ${escapeHTML(currentPageNum)} of ${escapeHTML(totalPagesNum)}`;

            document.getElementById('btn-search-prev').disabled = dataPage.first || currentPageNum <= 1;
            document.getElementById('btn-search-next').disabled = dataPage.last || currentPageNum >= totalPagesNum;
        }
    } catch (e) {
        console.error("Could not fetch search results: ", e);
    }
}

window.openEditAttendanceModal = async (log) => {
    const newStatus = prompt(`Editing attendance for ${escapeHTML(log.studentName)} on ${escapeHTML(log.date)} (${escapeHTML(log.session)})\nCurrent Status: ${escapeHTML(log.status)}\nEnter new status (P, A, OD):`);
    if (!newStatus) return;
    let statusStr = '';
    if (newStatus.toUpperCase() === 'P') statusStr = 'PRESENT';
    else if (newStatus.toUpperCase() === 'A') statusStr = 'ABSENT';
    else if (newStatus.toUpperCase() === 'OD') statusStr = 'ON_DUTY';
    else {
        showToast('Invalid status. Use P, A, or OD.', 'error');
        return;
    }

    try {
        await api.put(`/admin/attendance/${log.id || log.attendanceId}`, { status: statusStr });
        showToast('Attendance updated successfully', 'success');
        loadAttendanceLogsSearch(currentSearchPage);
    } catch (e) {
        showToast(e.message || 'Failed to update attendance', 'error');
    }
};

async function loadAuditLogs(page) {
    currentAuditPage = page;
    const tableBody = document.querySelector('#audit-logs-table tbody');
    if (tableBody) {
        tableBody.innerHTML = `<tr><td colspan="7" class="text-center">Loading audit logs…</td></tr>`;
    }
    
    try {
        const response = await api.get(`/admin/audit-logs?page=${escapeHTML(page)}&size=10`);
        const tableBody = document.querySelector('#audit-logs-table tbody');
        if (response && response.data && tableBody) {
            const dataPage = response.data;
            const logs = dataPage.content || [];

            if (logs.length === 0) {
                tableBody.innerHTML = `<tr><td colspan="7"><div class="empty-state"><h4>No audit records found.</h4></div></td></tr>`;
                document.getElementById('audit-pagination-controls').classList.add('hidden');
            } else {
                tableBody.innerHTML = logs.map((item, index) => {
                    const serialNumber = (page * 10) + index + 1;
                    const formattedTime = item.timestamp ? formatDateTime(item.timestamp) : '';
                    return `
                        <tr>
                            <td>${escapeHTML(serialNumber)}</td>
                            <td>${escapeHTML(formattedTime)}</td>
                            <td>${escapeHTML(item.userId)}</td>
                            <td>${item.role || '-'}</td>
                            <td>${item.entityName || '-'}</td>
                            <td>${escapeHTML(item.action)}</td>
                            <td>${item.ipAddress || 'UNKNOWN'}</td>
                        </tr>
                    `;
                }).join('');
            }

            const currentPageNum = (dataPage.number !== undefined ? dataPage.number : (dataPage.pageable ? dataPage.pageable.pageNumber : page)) + 1;
            const totalPagesNum = Math.max(dataPage.totalPages !== undefined ? dataPage.totalPages : 1, 1);
            document.getElementById('audit-pagination-info').textContent = `Page ${escapeHTML(currentPageNum)} of ${escapeHTML(totalPagesNum)}`;

            document.getElementById('btn-audit-prev').disabled = dataPage.first || currentPageNum <= 1;
            document.getElementById('btn-audit-next').disabled = dataPage.last || currentPageNum >= totalPagesNum;
        }
    } catch (e) {
        console.error("Could not fetch audit logs: ", e);
    }
}

async function generateReportPdf() {
    const type = document.getElementById('export-frequency').value;
    try {
        const query = new URLSearchParams();
        if (type === 'DAILY') {
            const date = document.getElementById('export-date').value;
            if (!date) { showToast('Please select a date', 'error'); return; }
            query.append('date', date);
        } else {
            const year = String(new Date().getFullYear()); // Or from a dropdown if added
            const month = document.getElementById('export-month').value;
            query.append('year', year);
            query.append('month', month);
        }

        const session = document.getElementById('export-session').value;
        const dept = document.getElementById('export-dept').value;
        const academicYear = document.getElementById('export-year').value;

        if (session) query.append('session', session);
        if (dept) query.append('departmentId', dept);
        if (academicYear) query.append('academicYear', academicYear);

        const url = `/admin/reports/${type.toLowerCase()}?${query.toString()}`;
        const res = await api.get(url);
        if (type === 'DAILY') {
            generatePDFReport('Daily Attendance Report', res.data, ['S.No', 'Register Number', 'Student Name', 'Session', 'Status', 'Evening Enabled']);
        } else {
            generatePDFReport('Monthly Attendance Report', res.data, ['S.No', 'Register Number', 'Student Name', 'Total Present', 'Total Absent', 'Total Leave', '%', 'Evening Enabled']);
        }
    } catch (e) {
        console.error(e);
        showToast('Failed to generate report', 'error');
    }
}

function generatePDFReport(title, data, columns) {
    if (!window.jspdf || !window.jspdf.jsPDF) {
        alert("jsPDF is not loaded properly.");
        return;
    }
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF('p', 'pt', 'a4');

    // Header section
    doc.setFontSize(18);
    doc.setFont("helvetica", "bold");
    doc.text("J.J. College of Engineering and Technology", 40, 40);
    doc.setFontSize(12);
    doc.setFont("helvetica", "normal");
    doc.text("(Autonomous)", 40, 55);
    doc.setFontSize(14);
    doc.setFont("helvetica", "bold");
    doc.text("Attendance ERP", 40, 75);
    doc.setFontSize(12);
    doc.setFont("helvetica", "normal");
    doc.text(title, 40, 95);

    const now = new Date();
    doc.setFontSize(8);
    doc.text(`Generated Date: ${formatDate(now)}`, 400, 40);
    doc.text(`Generated Time: ${now.toLocaleTimeString('en-US')}`, 400, 55);

    // Table data mapping
    let tableData = [];
    if (title.includes('Daily')) {
        tableData = data.map((d, i) => [
            i + 1,
            d.registerNumber || '',
            d.studentName || '',
            d.session || '',
            d.status || '',
            d.eveningClassEnabled ? 'Yes' : 'No'
        ]);
    } else {
        const studentMap = {};
        data.forEach(d => {
            if (!studentMap[d.studentId]) {
                studentMap[d.studentId] = {
                    registerNumber: d.registerNumber,
                    name: d.studentName,
                    eveningClassEnabled: d.eveningClassEnabled,
                    P: 0, A: 0, OD: 0, TOTAL: 0
                };
            }
            if (d.status === 'P') studentMap[d.studentId].P++;
            else if (d.status === 'A') studentMap[d.studentId].A++;
            else if (d.status === 'OD') studentMap[d.studentId].OD++;
            studentMap[d.studentId].TOTAL++;
        });

        tableData = Object.values(studentMap).map((d, i) => {
            const perc = d.TOTAL > 0 ? ((d.P + d.OD) / d.TOTAL * 100).toFixed(2) + '%' : '0%';
            return [
                i + 1,
                d.registerNumber || '',
                d.name || '',
                (d.P + d.OD).toString(),
                d.A.toString(),
                '0',
                perc,
                d.eveningClassEnabled ? 'Yes' : 'No'
            ];
        });
    }

    doc.autoTable({
        head: [columns],
        body: tableData,
        startY: 110,
        theme: 'grid',
        headStyles: {
            fillColor: [13, 36, 64],
            textColor: [255, 255, 255],
            fontStyle: 'bold',
            halign: 'left'
        },
        alternateRowStyles: {
            fillColor: [245, 248, 251]
        },
        styles: {
            fontSize: 10,
            cellPadding: 6,
            lineColor: [229, 234, 240],
            lineWidth: 0.5
        }
    });

    const finalY = (doc.lastAutoTable && doc.lastAutoTable.finalY) ? doc.lastAutoTable.finalY + 25 : 150;
    doc.setFontSize(9);
    doc.setTextColor(13, 36, 64);
    doc.text("J.J. College of Engineering and Technology — Attendance ERP", 40, finalY);

    const pdfName = title.replace(/\s+/g, '_').toLowerCase() + "_" + now.getTime() + ".pdf";
    doc.save(pdfName);
}

// Session Override & Unlock Form Functions
window.applyLockOverride = async function(isUnlocked) {
    const date = document.getElementById('unlock-date').value;
    const session = document.getElementById('unlock-session-select').value;
    const teacherId = document.getElementById('unlock-teacher-select')?.value || null;
    const durationHours = document.getElementById('unlock-duration-select')?.value || null;

    if (!date || !session) {
        showToast("Please select date and session.", "error");
        return;
    }

    let reason = null;
    if (isUnlocked) {
        reason = window.prompt('Please give a reason for unlocking this session:');
        if (reason === null) return; // cancelled
        if (!reason.trim()) {
            showToast('A reason is required to unlock a session.', 'error');
            return;
        }
    }

    try {
        await api.post('/admin/attendance/unlock', {
            date,
            session,
            unlocked: isUnlocked,
            reason: reason ? reason.trim() : null,
            teacherId: teacherId ? parseInt(teacherId, 10) : null,
            durationHours: durationHours ? parseFloat(durationHours) : null
        });
        showToast(`✓ Attendance session ${escapeHTML(session)} on ${escapeHTML(date)} is now ${isUnlocked ? 'Unlocked' : 'Locked'}`, 'success');
        loadLockStatus();
    } catch(e) {
        showToast(e.message || "Failed to update lock status", "error");
    }
};


function initPasswordToggles() {
    if (window._passwordTogglesInitialized) return;
    window._passwordTogglesInitialized = true;
    document.addEventListener('click', function(e) {
        const toggleBtn = e.target.closest('#toggle-t-password, #toggle-edit-t-password');
        if (!toggleBtn) return;
        e.preventDefault();
        const isEdit = toggleBtn.id === 'toggle-edit-t-password';
        const pwdInput = document.getElementById(isEdit ? 'edit-t-password' : 't-password');
        if (pwdInput) {
            const isPassword = pwdInput.type === 'password';
            pwdInput.type = isPassword ? 'text' : 'password';
            toggleBtn.setAttribute('aria-pressed', isPassword.toString());
        }
    });
};

// ---------------------------------------------------------------------------
// Import Students (CSV)
// ---------------------------------------------------------------------------

let csvSelectedFile = null;
let csvImportInitialized = false;

function initCsvImportPage() {
    if (csvImportInitialized) return;
    csvImportInitialized = true;

    const templateLink = document.getElementById('csv-template-link');
    if (templateLink) {
        templateLink.addEventListener('click', async (e) => {
            e.preventDefault();
            try {
                const response = await fetch(`${escapeHTML(API_BASE_URL)}/admin/students/csv/template`, {
                    headers: { 'Authorization': `Bearer ${api.getToken()}` }
                });
                if (!response.ok) throw new Error('Could not download the template.');
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'jjcet_erp_student_import_template.csv';
                document.body.appendChild(a);
                a.click();
                a.remove();
                window.URL.revokeObjectURL(url);
            } catch (err) {
                showToast(err.message || 'Could not download the template.', 'error');
            }
        });
    }

    const fileInput = document.getElementById('csv-file-input');
    if (fileInput) {
        fileInput.addEventListener('change', () => {
            csvSelectedFile = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
            document.getElementById('csv-preview-panel').classList.add('hidden');
        });
    }

    const previewBtn = document.getElementById('csv-preview-btn');
    if (previewBtn) previewBtn.addEventListener('click', runCsvPreview);

    const confirmBtn = document.getElementById('csv-confirm-btn');
    if (confirmBtn) confirmBtn.addEventListener('click', runCsvConfirm);
}

async function runCsvPreview() {
    if (!csvSelectedFile) {
        showToast('Please select a CSV file first.', 'error');
        return;
    }
    const btn = document.getElementById('csv-preview-btn');
    btn.disabled = true;
    btn.textContent = 'Checking...';
    try {
        const formData = new FormData();
        formData.append('file', csvSelectedFile);
        const res = await api.uploadFile('/admin/students/csv/preview', formData);
        renderCsvSummary(res.data, false);
        showMessage('csv-import-message', 'Preview ready. Nothing has been saved yet. Review the rows below, then click Import Students to save.', 'info');
    } catch (e) {
        showMessage('csv-import-message', e.message || 'Could not read the CSV file.', 'danger');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Check File';
    }
}

async function runCsvConfirm() {
    if (!csvSelectedFile) {
        showToast('Please select a CSV file first.', 'error');
        return;
    }
    if (!window.confirm('This will save the changes shown in the preview. Continue?')) return;

    const btn = document.getElementById('csv-confirm-btn');
    btn.disabled = true;
    btn.textContent = 'Importing...';
    try {
        const formData = new FormData();
        formData.append('file', csvSelectedFile);
        const res = await api.uploadFile('/admin/students/csv/confirm', formData);
        renderCsvSummary(res.data, true);
        showToast('Students imported successfully.', 'success');
        if (typeof requestOnce === 'function') {
            // Force a fresh students list and update dropdowns next time.
            window._studentsListStale = true;
            if (typeof loadAllDropdownsAndCRUDs === 'function') loadAllDropdownsAndCRUDs();
        }
    } catch (e) {
        showMessage('csv-import-message', e.message || 'Import failed. No changes were saved.', 'danger');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Import Students';
    }
}

function renderCsvSummary(summary, imported) {
    const panel = document.getElementById('csv-preview-panel');
    const cardsEl = document.getElementById('csv-summary-cards');
    const rowsEl = document.getElementById('csv-preview-rows');
    if (!panel || !cardsEl || !rowsEl || !summary) return;

    panel.classList.remove('hidden');

    const cards = [
        ['Total Rows', summary.totalRows],
        ['New Students', summary.newStudents],
        ['Students to Update', summary.studentsToUpdate],
        ['Unchanged Students', summary.unchangedStudents],
        ['New Departments', summary.newDepartments],
        ['New Batches', summary.newBatches],
        ['Rows With Errors', summary.rowsWithErrors]
    ];
    cardsEl.innerHTML = cards.map(([label, value]) => `
        <div style="text-align:center; padding: 10px; background: white; border-radius: 6px; border: 1px solid #e2e8f0;">
            <div style="font-size: 22px; font-weight: 700; color: var(--primary-color, #1e293b);">${escapeHTML(value)}</div>
            <div style="font-size: 12px; color: #64748b;">${escapeHTML(label)}</div>
        </div>
    `).join('');

    const statusLabels = {
        NEW_STUDENT: 'New student',
        UPDATE_EXISTING_STUDENT: 'Will be updated',
        UNCHANGED_STUDENT: 'No changes',
        ROW_ERROR: 'Error'
    };

    rowsEl.innerHTML = (summary.rows || []).map(r => {
        const label = statusLabels[r.status] || r.status;
        const isError = r.status === 'ROW_ERROR';
        return `<tr>
            <td>${escapeHTML(r.rowNumber)}</td>
            <td>${r.registerNumber || ''}</td>
            <td>${r.studentName || ''}</td>
            <td>${r.departmentName || r.departmentCode || ''}</td>
            <td style="color: ${isError ? '#dc2626' : '#16a34a'};">${escapeHTML(label)}${r.message && isError ? ': ' + r.message : ''}</td>
        </tr>`;
    }).join('');

    if (imported) {
        document.getElementById('csv-confirm-btn').classList.add('hidden');
    } else {
        document.getElementById('csv-confirm-btn').classList.remove('hidden');
    }
}

async function loadAllDropdownsAndCRUDs() {
    await loadMasterData();

    if (typeof loadStudentsCRUD === 'function') loadStudentsCRUD();
    if (typeof loadTeachersCRUD === 'function') loadTeachersCRUD();

    const deptsSection = document.getElementById('manage-departments');
    if (deptsSection && !deptsSection.classList.contains('hidden')) {
        try {
            const res = await api.get('/admin/departments');
            if (typeof renderDepartmentsCRUD === 'function') renderDepartmentsCRUD(extractArray(res));
        } catch(e) { console.error(e); }
    }

    const batchesSection = document.getElementById('manage-batches');
    if (batchesSection && !batchesSection.classList.contains('hidden')) {
        try {
            const res = await api.get('/admin/batches');
            if (typeof renderBatchesCRUD === 'function') renderBatchesCRUD(extractArray(res));
        } catch(e) { console.error(e); }
    }

    if (typeof loadAnalyticsCenter === 'function') loadAnalyticsCenter();
}
window.loadAllDropdownsAndCRUDs = loadAllDropdownsAndCRUDs;

async function generateReportCsv() {
    const type = document.getElementById('export-frequency')?.value || 'DAILY';
    try {
        const query = new URLSearchParams();
        if (type === 'DAILY') {
            const date = document.getElementById('export-date')?.value;
            if (!date) { showToast('Please select a date', 'error'); return; }
            query.append('date', date);
        } else {
            const year = String(new Date().getFullYear());
            const month = document.getElementById('export-month')?.value || '1';
            query.append('year', year);
            query.append('month', month);
        }

        const session = document.getElementById('export-session')?.value || 'FN';
        const dept = document.getElementById('export-dept')?.value;
        const batch = document.getElementById('export-batch')?.value;
        const academicYear = document.getElementById('export-year')?.value;

        if (session) query.append('session', session);
        if (dept) query.append('departmentId', dept);
        if (batch) query.append('batchId', batch);
        if (academicYear) query.append('academicYear', academicYear);

        const url = `/admin/reports/${type.toLowerCase()}?${query.toString()}`;
        const res = await api.get(url);
        const records = res.data || [];

        let csvContent = "data:text/csv;charset=utf-8,";
        if (type === 'DAILY') {
            csvContent += "S.No,Register Number,Student Name,Session,Status,Evening Enabled\n";
            records.forEach((row, i) => {
                csvContent += `${i + 1},"${row.registerNumber || ''}","${row.studentName || ''}","${row.session || ''}","${row.status || ''}","${row.eveningClassEnabled ? 'YES' : 'NO'}"\n`;
            });
        } else {
            csvContent += "S.No,Register Number,Student Name,Total Present,Total Absent,Total Leave,Percentage,Evening Enabled\n";
            records.forEach((row, i) => {
                csvContent += `${i + 1},"${row.registerNumber || ''}","${row.studentName || ''}",${row.totalPresent || 0},${row.totalAbsent || 0},${row.totalLeave || 0},"${row.percentage || 0}%","${row.eveningClassEnabled ? 'YES' : 'NO'}"\n`;
            });
        }

        const encodedUri = encodeURI(csvContent);
        const link = document.createElement("a");
        link.setAttribute("href", encodedUri);
        link.setAttribute("download", `Attendance_Report_${type}_${new Date().toISOString().split('T')[0]}.csv`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        showToast('✓ CSV Report exported successfully', 'success');
    } catch (e) {
        console.error(e);
        showToast('Failed to export CSV report', 'error');
    }
}

async function generateReportExcel() {
    const type = document.getElementById('export-frequency')?.value || 'DAILY';
    try {
        const query = new URLSearchParams();
        if (type === 'DAILY') {
            const date = document.getElementById('export-date')?.value;
            if (!date) { showToast('Please select a date', 'error'); return; }
            query.append('date', date);
        } else {
            const year = String(new Date().getFullYear());
            const month = document.getElementById('export-month')?.value || '1';
            query.append('year', year);
            query.append('month', month);
        }

        const session = document.getElementById('export-session')?.value || 'FN';
        const dept = document.getElementById('export-dept')?.value;
        const batch = document.getElementById('export-batch')?.value;
        const academicYear = document.getElementById('export-year')?.value;

        if (session) query.append('session', session);
        if (dept) query.append('departmentId', dept);
        if (batch) query.append('batchId', batch);
        if (academicYear) query.append('academicYear', academicYear);

        const url = `/admin/reports/${type.toLowerCase()}?${query.toString()}`;
        const res = await api.get(url);
        const records = res.data || [];

        let excelContent = "S.No\tRegister Number\tStudent Name\tSession\tStatus\tEvening Enabled\n";
        if (type === 'DAILY') {
            records.forEach((row, i) => {
                excelContent += `${i + 1}\t${row.registerNumber || ''}\t${row.studentName || ''}\t${row.session || ''}\t${row.status || ''}\t${row.eveningClassEnabled ? 'YES' : 'NO'}\n`;
            });
        } else {
            excelContent = "S.No\tRegister Number\tStudent Name\tTotal Present\tTotal Absent\tTotal Leave\tPercentage\tEvening Enabled\n";
            records.forEach((row, i) => {
                excelContent += `${i + 1}\t${row.registerNumber || ''}\t${row.studentName || ''}\t${row.totalPresent || 0}\t${row.totalAbsent || 0}\t${row.totalLeave || 0}\t${row.percentage || 0}%\t${row.eveningClassEnabled ? 'YES' : 'NO'}\n`;
            });
        }

        const blob = new Blob([excelContent], { type: 'application/vnd.ms-excel' });
        const urlObj = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = urlObj;
        link.download = `Attendance_Report_${type}_${new Date().toISOString().split('T')[0]}.xls`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(urlObj);
        showToast('✓ Excel Report exported successfully', 'success');
    } catch (e) {
        console.error(e);
        showToast('Failed to export Excel report', 'error');
    }
}
