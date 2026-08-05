// Centralized date/time formatting for JJCET ERP.
// Do not add new inline toLocaleDateString()/toLocaleString() calls elsewhere - use these
// functions instead, so every page renders dates the same way and there's one place to fix
// bugs (timezone handling, invalid/null values, relative-time thresholds).
//
// NOTE: at the time this file was added, ~15 existing inline date-formatting call sites across
// admin.js and teacher.js were NOT yet migrated to use it (that migration is real work still
// pending - see JJCET_ERP_BUILD_REPORT.md). New code should use this module; a follow-up pass
// should replace the old inline calls one at a time and verify each page still renders correctly.

(function (global) {
    'use strict';

    function toDate(value) {
        if (value === null || value === undefined || value === '') return null;
        const d = value instanceof Date ? value : new Date(value);
        return isNaN(d.getTime()) ? null : d;
    }

    function isDateOnly(value) {
        // "2026-07-10" has no time component - parsing it as UTC midnight and then formatting
        // in the browser's local timezone can shift it to the previous day. Detect this shape
        // and parse it as a local calendar date instead.
        return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
    }

    function toLocalDateOnly(value) {
        const [y, m, d] = value.split('-').map(Number);
        return new Date(y, m - 1, d);
    }

    /** "10 July 2026" - date only, no time, no timezone shifting for date-only input. */
    function formatDate(value) {
        if (value === null || value === undefined || value === '') return '—';
        const d = isDateOnly(value) ? toLocalDateOnly(value) : toDate(value);
        if (!d) return '—';
        return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
    }

    /** "10 July 2026, 2:45 PM" - full precision, for audit detail / attendance edit history. */
    function formatDateTime(value) {
        const d = toDate(value);
        if (!d) return '—';
        return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })
            + ', ' + d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
    }

    /** "5 minutes ago" / "Today" / "Yesterday" / falls back to a full date beyond ~6 days. */
    function formatRelative(value) {
        const d = toDate(value);
        if (!d) return '—';
        const now = new Date();
        const diffMs = now.getTime() - d.getTime();

        if (diffMs < 0 && diffMs > -60000) return 'Just now'; // small clock-skew tolerance
        if (diffMs < 0) return formatDateTime(value); // future timestamp - show it plainly

        const diffSec = Math.floor(diffMs / 1000);
        if (diffSec < 60) return 'Just now';
        const diffMin = Math.floor(diffSec / 60);
        if (diffMin < 60) return diffMin + (diffMin === 1 ? ' minute ago' : ' minutes ago');
        const diffHr = Math.floor(diffMin / 60);
        if (diffHr < 24) return diffHr + (diffHr === 1 ? ' hour ago' : ' hours ago');

        const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const startOfValueDay = new Date(d.getFullYear(), d.getMonth(), d.getDate());
        const dayDiff = Math.round((startOfToday - startOfValueDay) / 86400000);

        if (dayDiff === 0) return 'Today';
        if (dayDiff === 1) return 'Yesterday';
        if (dayDiff > 1 && dayDiff < 7) return dayDiff + ' days ago';

        return formatDate(value);
    }

    /** "Updated 10 minutes ago" / "Updated on 10 July 2026" beyond the relative-time window. */
    function formatUpdated(value) {
        const rel = formatRelative(value);
        if (rel === '—') return '—';
        if (rel === 'Today' || rel === 'Yesterday' || /ago$/.test(rel)) return 'Updated ' + rel.charAt(0).toLowerCase() + rel.slice(1);
        return 'Updated on ' + rel;
    }

    /** "Started today at 9:30 AM" style, for session/attendance start displays. */
    function formatStarted(value) {
        const d = toDate(value);
        if (!d) return '—';
        const time = d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
        const rel = formatRelative(value);
        if (rel === 'Today') return 'Started today at ' + time;
        if (rel === 'Yesterday') return 'Started yesterday at ' + time;
        return 'Started on ' + formatDate(value) + ' at ' + time;
    }

    global.DateUtils = { formatDate, formatDateTime, formatRelative, formatUpdated, formatStarted };
})(window);

export const { formatDate, formatDateTime, formatRelative, formatUpdated, formatStarted } = window.DateUtils;
