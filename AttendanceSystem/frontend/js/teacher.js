import { escapeHTML } from './utils.js';


import api, { API_BASE_URL } from './api.js';
import { verifyRoleAccess, logoutUser, getCurrentUser, initSessionTimeout } from './auth.js';
import { showToast, clearForm, setupDashboard, initFormValidation, extractArray, populateSelect, requestOnce } from './utils.js';

let currentGridStudents = [];
let gridAttendance = {}; 

function initTeacherApp() {
    if (!verifyRoleAccess(['ADMIN', 'TEACHER'])) return;

    try {
        setupDashboard(logoutUser, getCurrentUser());
    } catch(e) {
        console.error("Teacher setupDashboard failed:", e);
    }

    const todayStr = new Date().toISOString().split('T')[0];
    const exportDate = document.getElementById('export-date');
    if (exportDate) exportDate.value = todayStr;
    const gridDate = document.getElementById('grid-date');
    if (gridDate) gridDate.value = todayStr;

    try {
        setupEventListeners();
    } catch(e) {
        console.error("Teacher setupEventListeners failed:", e);
    }

    try {
        initFormValidation();
    } catch(e) {
        console.error("Teacher initFormValidation failed:", e);
    }

    try {
        initClock();
    } catch(e) {
        console.error("Teacher initClock failed:", e);
    }

    Promise.all([
        loadTeacherDashboardStats().catch(e => console.warn("Failed loading stats:", e)),
        loadGridFilterOptions().catch(e => console.warn("Failed loading grid options:", e))
    ]).then(() => {
        loadAttendanceGrid().catch(e => console.warn("Failed loading attendance grid:", e));
    });

    try {
        initSessionTimeout('TEACHER');
    } catch(e) {
        console.error("Teacher initSessionTimeout failed:", e);
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTeacherApp);
} else {
    initTeacherApp();
}


let currentServerSession = 'CLOSED';
let sessionPollingTimer = null;

async function fetchCurrentSessionDetails() {
    try {
        const response = await api.get('/teacher/session/current');
        const sessionData = response.data;
        currentServerSession = sessionData.session;
        updateSessionCardsUI(sessionData);
    } catch(e) {
        console.warn("Unable to fetch sessions. Retrying...", e);
        const timingWarning = document.getElementById('timing-warning');
        if (timingWarning) {
            timingWarning.querySelector('div').innerHTML = '<strong>Unable to fetch sessions. Retrying...</strong> Automatic retry in 5 seconds.';
            timingWarning.classList.remove('hidden');
        }
    }
}

function updateSessionCardsUI(sessionData) {
    const activeSession = sessionData.session;
    const sessions = ['FN', 'AN', 'EN'];
    sessions.forEach(s => {
        const card = document.getElementById(`card-${escapeHTML(s)}`);
        const statusBadge = document.getElementById(`badge-${escapeHTML(s)}-status`);
        const detailEl = document.getElementById(`badge-${escapeHTML(s)}-detail`);
        if (!card || !statusBadge || !detailEl) return;

        const state = sessionData.sessions ? sessionData.sessions[s] : (s === activeSession ? 'ACTIVE' : 'CLOSED');

        if (state === 'ACTIVE') {
            card.style.borderColor = 'var(--success-color)';
            card.style.backgroundColor = '#f0fdf4';
            card.style.boxShadow = '0 4px 12px rgba(22, 163, 74, 0.08)';
            statusBadge.textContent = 'OPEN';
            statusBadge.style.backgroundColor = 'var(--success-color)';
            statusBadge.style.color = 'white';
            detailEl.textContent = 'Marking Open';
            detailEl.style.color = 'var(--success-color)';
            detailEl.style.fontWeight = 'bold';
        } else {
            card.style.borderColor = 'var(--border-color)';
            card.style.backgroundColor = 'var(--bg-color)';
            card.style.boxShadow = 'none';

            statusBadge.textContent = 'CLOSED';
            statusBadge.style.backgroundColor = '#e2e8f0';
            statusBadge.style.color = '#475569';

            detailEl.textContent = 'Disabled';
            detailEl.style.color = 'var(--text-secondary)';
            detailEl.style.fontWeight = 'normal';
        }
    });

    const timingWarning = document.getElementById('timing-warning');
    const loadBtn = document.querySelector('#grid-filter-form button[type="submit"]');
    const saveBtn = document.getElementById('btn-save-attendance');
    const allPBtn = document.getElementById('btn-all-present');
    const allABtn = document.getElementById('btn-all-absent');
    const resetBtn = document.getElementById('btn-reset-grid');

    if (activeSession === 'CLOSED' || !activeSession) {
        if (timingWarning) {
            timingWarning.querySelector('div').innerHTML = '<strong>Attendance Closed:</strong> You cannot load or mark attendance at this time. Operations will automatically unlock during valid session hours or via admin override.';
            timingWarning.classList.remove('hidden');
        }
        if (loadBtn) loadBtn.disabled = true;
        if (saveBtn) saveBtn.disabled = true;
        if (allPBtn) allPBtn.disabled = true;
        if (allABtn) allABtn.disabled = true;
        if (resetBtn) resetBtn.disabled = true;
    } else {
        if (timingWarning) timingWarning.classList.add('hidden');
        if (loadBtn) loadBtn.disabled = false;
        if (saveBtn) saveBtn.disabled = false;
        if (allPBtn) allPBtn.disabled = false;
        if (allABtn) allABtn.disabled = false;
        if (resetBtn) resetBtn.disabled = false;
    }
}

let clockTimer = null;
function initClock() {
    const timeDisplay = document.getElementById('current-time-display');
    function tick() {
        const now = new Date();
        if (timeDisplay) {
            timeDisplay.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' | ' + now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
        }
    }
    tick();
    if (!clockTimer) {
        clockTimer = setInterval(tick, 1000);
    }
    fetchCurrentSessionDetails();
    if (!sessionPollingTimer) {
        sessionPollingTimer = setInterval(fetchCurrentSessionDetails, 5000);
    }
}


async function loadGridFilterOptions() {
    try {
        const [deptsRes, batchRes] = await Promise.all([
            requestOnce('departments', () => api.get('/admin/departments')).catch(() => []),
            requestOnce('batches', () => api.get('/admin/batches')).catch(() => [])
        ]);

        const depts = extractArray(deptsRes);
        const activeDepts = depts.filter(d => d.active);

        const gridDept = document.getElementById('grid-dept');
        const exportDept = document.getElementById('export-dept');

        if (gridDept && gridDept.dataset.locked !== "true") {
            gridDept.disabled = false;
            populateSelect(gridDept, activeDepts, { placeholder: "All Departments" });
        }
        if (exportDept && exportDept.dataset.locked !== "true") {
            exportDept.disabled = false;
            populateSelect(exportDept, activeDepts, { placeholder: "All Departments" });
        }

        const batches = extractArray(batchRes);
        const activeBatches = batches.filter(b => b.active);
        window._allActiveBatchesTeacher = activeBatches;
        populateSelect(document.getElementById('grid-batch'), activeBatches, { placeholder: "All Batches" });
    } catch (e) {
        console.error('Failed to load filter options:', e);
        const gridDept = document.getElementById('grid-dept');
        if (gridDept && gridDept.options.length <= 1) {
            gridDept.innerHTML = '<option value="">Unable to load departments</option>';
            gridDept.disabled = false;
        }
    }
}

function updateTeacherBatchDropdown(deptId) {
    const batchSelect = document.getElementById('grid-batch');
    if (!batchSelect) return;
    const allBatches = window._allActiveBatchesTeacher || [];
    if (!deptId) {
        populateSelect(batchSelect, allBatches, { placeholder: "All Batches" });
        return;
    }
    const filtered = allBatches.filter(b => b.department && b.department.id == deptId);
    populateSelect(batchSelect, filtered.length > 0 ? filtered : allBatches, { placeholder: "All Batches" });
}

function setupEventListeners() {
    const gridForm = document.getElementById('grid-filter-form');
    if (gridForm) {
        gridForm.addEventListener('submit', (e) => {
            e.preventDefault();
            loadAttendanceGrid();
        });

        const gridDept = document.getElementById('grid-dept');
        if (gridDept) {
            gridDept.addEventListener('change', (e) => {
                updateTeacherBatchDropdown(e.target.value);
                loadAttendanceGrid();
            });
        }

        ['grid-batch', 'grid-year', 'grid-date'].forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('change', () => {
                    loadAttendanceGrid();
                });
            }
        });
        const gridSearch = document.getElementById('grid-search');
        if (gridSearch) {
            gridSearch.addEventListener('input', () => {
                clearTimeout(window.gridSearchTimeout);
                window.gridSearchTimeout = setTimeout(() => {
                    loadAttendanceGrid();
                }, 300);
            });
        }
    }

    const btnAllP = document.getElementById('btn-all-present');
    if (btnAllP) btnAllP.addEventListener('click', () => markGridAll('P'));

    const btnAllA = document.getElementById('btn-all-absent');
    if (btnAllA) btnAllA.addEventListener('click', () => markGridAll('A'));

    const btnSaveGrid = document.getElementById('btn-save-attendance');
    if (btnSaveGrid) btnSaveGrid.addEventListener('click', showMarkEnterModal);


    const btnExportPdf = document.getElementById('btn-export-pdf');
    if (btnExportPdf) btnExportPdf.addEventListener('click', () => window.print());

    const btnResetGrid = document.getElementById('btn-reset-grid');
    if (btnResetGrid) btnResetGrid.addEventListener('click', resetGrid);

    const markattendInput = document.getElementById('markattend-input');
    const markattendConfirmBtn = document.getElementById('btn-markattend-confirm');
    const markattendCancelBtn = document.getElementById('btn-markattend-cancel');

    if (markattendInput) {
        markattendInput.addEventListener('input', () => {
            if (markattendConfirmBtn) {
                markattendConfirmBtn.disabled = markattendInput.value !== 'MARKATTEND';
            }
        });
    }
    if (markattendCancelBtn) {
        markattendCancelBtn.addEventListener('click', closeMarkEnterModal);
    }
    if (markattendConfirmBtn) {
        markattendConfirmBtn.addEventListener('click', async () => {
            const inputEl = document.getElementById('markattend-input');
            if (inputEl && inputEl.value === 'MARKATTEND') {
                closeMarkEnterModal();
                await saveAttendanceGrid();
            }
        });
    }
}


function showMarkEnterModal() {
    if (currentGridStudents.length === 0) {
        showToast('No students loaded. Please load attendance grid first.', 'warning');
        return;
    }
    const anySelected = Object.values(gridAttendance).some(val => val !== null);
    if (!anySelected) {
        showToast('No attendance statuses selected. Please mark at least one student.', 'warning');
        return;
    }

    const modal = document.getElementById('markattend-modal');
    const input = document.getElementById('markattend-input');
    if (modal) {
        if (input) input.value = '';
        const confirmBtn = document.getElementById('btn-markattend-confirm');
        if (confirmBtn) confirmBtn.disabled = true;
        modal.style.display = 'flex';
        modal.classList.remove('hidden');
        if (input) input.focus();
    }
}

function closeMarkEnterModal() {
    const modal = document.getElementById('markattend-modal');
    if (modal) {
        modal.style.display = 'none';
        modal.classList.add('hidden');
    }
}

function getCurrentSessionDetails() {
    return {
        date: document.getElementById('grid-date')?.value || new Date().toISOString().split('T')[0],
        session: currentServerSession !== 'CLOSED' ? currentServerSession : null
    };
}

let isLoadingGrid = false;
async function loadAttendanceGrid() {
    if (isLoadingGrid) return;
    isLoadingGrid = true;

    const deptId = document.getElementById('grid-dept').value;
    const batchId = document.getElementById('grid-batch').value;
    const year = document.getElementById('grid-year').value;
    const query = document.getElementById('grid-search').value.trim();
    const date = document.getElementById('grid-date').value;

    const sessionInfo = getCurrentSessionDetails();
    const activeSession = sessionInfo.session;

    if (!activeSession) {
        showToast('Cannot load students outside session hours.', 'warning');
        return;
    }

    const btn = document.querySelector('#grid-filter-form button[type="submit"]');
    if (btn) { btn.disabled = true; btn.textContent = 'Loading data...'; }

    const container = document.getElementById('attendance-cards-grid');
    if (container) {
        container.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: var(--spacing-xl); color: var(--text-secondary);">
            <h4>Loading students...</h4>
            <p>Please wait while we fetch the records from the server.</p>
        </td></tr>`;
    }

    try {
        let url = `/teacher/students/grid?`;
        if (deptId) url += `departmentId=${deptId}&`;
        if (batchId) url += `batchId=${escapeHTML(batchId)}&`;
        if (year) url += `year=${escapeHTML(year)}&`;
        if (query) url += `query=${encodeURIComponent(query)}`;

        const studentRes = await api.get(url);
        currentGridStudents = studentRes.data || [];

        const logsRes = await api.get(`/teacher/attendance?date=${escapeHTML(date)}&session=${activeSession}${deptId ? `&departmentId=${deptId}` : ''}`);
        const logsMap = {};
        (logsRes.data || []).forEach(log => {
            logsMap[log.studentId] = log.status;
        });

        gridAttendance = {};
        currentGridStudents.forEach(s => {
            const isEnExempt = activeSession === 'EN' && !s.eveningClassEnabled;
            if (isEnExempt) {
                gridAttendance[s.id] = null;
            } else {
                gridAttendance[s.id] = logsMap[s.id] || 'P'; 
            }
        });

        renderAttendanceCards();
    } catch (e) {
        console.error('Grid fetch error:', e);
        if (container) {
            container.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: var(--spacing-xl); color: var(--error-color);">
                <h4>Error Loading Grid</h4>
                <p>${e.message || 'Failed to connect to server. Please try again.'}</p>
            </td></tr>`;
        }
        showToast('Failed to load attendance grid', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Load Students'; }
    }
}

function renderAttendanceCards() {
    const container = document.getElementById('attendance-cards-grid');
    if (!container) return;

    if (currentGridStudents.length === 0) {
        container.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: var(--spacing-xl); color: var(--text-secondary);">
            <h4>No attendance records available for selected filters</h4>
            <p>Adjust your filters or try a different search query.</p>
        </td></tr>`;
        updateGridStatsSummary();
        return;
    }

    const sessionInfo = getCurrentSessionDetails();
    const activeSession = sessionInfo.session;

    container.innerHTML = currentGridStudents.map((s) => {
        const currentStatus = gridAttendance[s.id];
        const isEveningSession = activeSession === 'EN';
        const isEnExempt = isEveningSession && !s.eveningClass;

        let actionHtml = '';
        if (!activeSession) {
            actionHtml = `<div style="color: var(--error-color); font-size: 13px; font-weight: 500; text-align: center;">Attendance Closed</div>`;
        } else if (isEnExempt) {
            actionHtml = `<div style="color: var(--text-secondary); font-size: 13px; font-style: italic; text-align: center; padding: 10px; background: #f1f5f9; border-radius: 6px;">Day Scholar (Exempt)</div>`;
        } else {
            const isPActive = currentStatus === 'P';
            actionHtml = `
                <div style="display: flex; gap: var(--spacing-sm); width: 100%;">
                    <button type="button" class="btn btn-attendance-toggle" 
                            onclick="toggleStudentAttendance(${s.id})" 
                            style="flex: 1; padding: 10px; border-radius: 6px; font-weight: 600; cursor: pointer; transition: all 0.2s ease;
                            border: none; 
                            background-color: ${isPActive ? 'var(--success-color)' : 'var(--error-color)'}; 
                            color: white;">
                        ${isPActive ? 'P' : 'AB'}
                    </button>
                </div>
            `;
        }

        return `
            <tr data-student-id="${s.id}" style="transition: background-color 0.2s;">
                <td style="font-family: monospace; vertical-align: middle;">${escapeHTML(s.registerNumber)}</td>
                <td style="vertical-align: middle; font-weight: 600;">${escapeHTML(s.name)}</td>
                <td style="vertical-align: middle; font-size: 13px; color: var(--text-secondary);">
                    Year ${s.year || 1} • ${s.departmentName || ''}
                </td>
                <td style="vertical-align: middle;">
                    <span style="font-size: 11px; padding: 4px 8px; border-radius: 4px; font-weight: bold; background: ${s.type === 'HOSTEL' ? '#eff6ff' : '#f3f4f6'}; color: ${s.type === 'HOSTEL' ? '#1e40af' : '#4b5563'};">
                        ${s.type === 'HOSTEL' ? 'Hosteler' : 'Day Scholar'}
                    </span>
                </td>
                <td style="vertical-align: middle; min-width: 180px;">
                    ${actionHtml}
                </td>
            </tr>
        `;
    }).join('');

    updateGridStatsSummary();
}

function updateGridStatsSummary() {
    let total = currentGridStudents.length;
    let present = 0;
    let absent = 0;

    currentGridStudents.forEach(s => {
        const status = gridAttendance[s.id];
        if (status === 'P') present++;
        else if (status === 'A') absent++;
    });

    const pct = total > 0 ? ((present / total) * 100).toFixed(2) : "0.00";

    const totEl = document.getElementById('summary-total');
    const prEl = document.getElementById('summary-present');
    const abEl = document.getElementById('summary-absent');
    const pctEl = document.getElementById('summary-percentage');

    if (totEl) totEl.textContent = total;
    if (prEl) prEl.textContent = present;
    if (abEl) abEl.textContent = absent;
    if (pctEl) pctEl.textContent = `${pct}%`;
}

window.toggleStudentAttendance = (studentId) => {
    gridAttendance[studentId] = gridAttendance[studentId] === 'P' ? 'A' : 'P';
    renderAttendanceCards();
};

function markGridAll(statusValue) {
    const sessionInfo = getCurrentSessionDetails();
    const activeSession = sessionInfo.session;
    if (!activeSession) {
        showToast('Cannot mark attendance outside session hours.', 'warning');
        return;
    }
    currentGridStudents.forEach(s => {
        const isDayScholar = s.type === 'DAY_SCHOLAR';
        const isEveningSession = activeSession === 'EN';
        const isEnExempt = isDayScholar && isEveningSession;
        if (!isEnExempt) {
            gridAttendance[s.id] = statusValue;
        }
    });
    renderAttendanceCards();
}

function resetGrid() {
    currentGridStudents.forEach(s => {
        const isEnExempt = s.type === 'DAY_SCHOLAR' && currentServerSession === 'EN';
        if (!isEnExempt) {
            gridAttendance[s.id] = 'P'; 
        }
    });
    renderAttendanceCards();
}

async function saveAttendanceGrid() {
    const dateEl = document.getElementById('grid-date');
    const date = dateEl ? dateEl.value : '';

    if (!date) {
        showToast('Please select a date', 'warning');
        return;
    }

    const sessionInfo = getCurrentSessionDetails();
    const activeSession = sessionInfo.session;
    if (!activeSession) {
        showToast('Cannot save attendance outside permitted session hours.', 'error');
        return;
    }

    const requests = [];
    currentGridStudents.forEach(s => {
        const status = gridAttendance[s.id];
        if (status) {
            requests.push({ studentId: s.id, session: activeSession, status: status });
        }
    });

    if (requests.length === 0) {
        showToast('No attendance statuses selected', 'warning');
        return;
    }

    const btn = document.getElementById('btn-save-attendance');
    if (btn) { btn.disabled = true; btn.textContent = 'Saving...'; }

    try {
        await api.post('/teacher/attendance/bulk', requests);
        showToast(`✓ Successfully saved attendance for ${escapeHTML(requests.length)} students`, 'success');
        loadAttendanceGrid();
    } catch (e) {
        showToast(e.message || 'Failed to save attendance', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'MARK ATTEND'; }
    }
}


async function loadTeacherDashboardStats() {
    try {
        const response = await api.get('/teacher/dashboard/stats');
        if (!response || !response.data) return;
        const d = response.data;

        const deptNameEl = document.getElementById('teacher-dept-name');
        if (deptNameEl) deptNameEl.textContent = d.name || 'All Departments';

        const gridDept = document.getElementById('grid-dept');
        const exportDept = document.getElementById('export-dept');

        if (d.departmentId) {
            if (gridDept) {
                gridDept.innerHTML = `<option value="${d.departmentId}">${escapeHTML(d.name)}</option>`;
                gridDept.disabled = true;
                gridDept.dataset.locked = "true";
            }
            if (exportDept) {
                exportDept.innerHTML = `<option value="${d.departmentId}">${escapeHTML(d.name)}</option>`;
                exportDept.disabled = true;
                exportDept.dataset.locked = "true";
            }
        } else {
            if (gridDept) {
                gridDept.dataset.locked = "false";
                gridDept.disabled = false;
            }
            if (exportDept) {
                exportDept.dataset.locked = "false";
                exportDept.disabled = false;
            }
            loadGridFilterOptions().catch(() => {});
        }
    } catch (e) {
        console.warn('Could not load teacher dashboard stats:', e);
        const deptNameEl = document.getElementById('teacher-dept-name');
        if (deptNameEl) deptNameEl.textContent = 'General Department';
        loadGridFilterOptions().catch(() => {});
    }
}

// 30-Second Auto Refresh Interval for background synchronization
let autoRefresh30sTimer = null;
if (!autoRefresh30sTimer) {
    autoRefresh30sTimer = setInterval(() => {
        fetchCurrentSessionDetails().catch(() => {});
        loadTeacherDashboardStats().catch(() => {});
    }, 30000);
}


