

import api from './api.js';
import { showMessage, clearForm } from './utils.js';

export async function submitAttendanceRecord(event) {
    event.preventDefault();

    const payload = {
        studentId: parseInt(document.getElementById('a-student-id').value, 10),
        date: document.getElementById('a-date').value,
        session: document.getElementById('a-session').value,
        present: document.getElementById('a-status').value === "true"
    };

    try {
        await api.post('/attendance', payload);
        showMessage('attendance-message', 'Attendance Saved Successfully', 'success');
        clearForm('attendance-form');
        document.getElementById('a-date').value = new Date().toISOString().split('T')[0];
    } catch (error) {
        showMessage('attendance-message', error.message || 'Failed to save attendance', 'error');
    }
}

export async function fetchStudentReport(event) {
    event.preventDefault();

    const studentId = parseInt(document.getElementById('r-student-id').value, 10);
    if (!studentId) return;

    try {
        const response = await api.get(`/attendance/summary/${studentId}`);
        const data = response.data;
        if (data) {
            document.getElementById('res-total').textContent = data.totalClasses || 0;
            document.getElementById('res-present').textContent = data.presentDays || 0;
            document.getElementById('res-absent').textContent = data.absentDays || 0;
            const percentage = data.totalClasses > 0 
                ? ((data.presentDays / data.totalClasses) * 100).toFixed(2) + '%' 
                : '0%';
            document.getElementById('res-percent').textContent = percentage;
            document.getElementById('report-results').classList.remove('hidden');
            showMessage('report-message', 'Report loaded', 'success');
        }
    } catch (error) {
        document.getElementById('report-results').classList.add('hidden');
        showMessage('report-message', error.message || 'Failed to fetch report', 'error');
    }
}

export function initAttendance() {
    const dateInput = document.getElementById('a-date');
    if (dateInput) {
        dateInput.value = new Date().toISOString().split('T')[0];
    }

    const attendanceForm = document.getElementById('attendance-form');
    if (attendanceForm) {
        attendanceForm.addEventListener('submit', submitAttendanceRecord);
    }

    const reportForm = document.getElementById('report-form');
    if (reportForm) {
        reportForm.addEventListener('submit', fetchStudentReport);
    }
}

