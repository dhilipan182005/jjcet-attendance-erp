# Workflow-doc vs. codebase check — 10 July 2026

Checked each of the 14 pages in the workflow report against the actual code.
Only pure *additions* were made below — no existing function's behavior was changed.

## Added (safe, additive only)

1. **LOGIN_FAILURE / ACCOUNT_LOCKED audit logging** (`AuthService.java`)
   Report requires `LOGIN_SUCCESS / LOGIN_FAILURE` in Audit Logs. `LOGIN_SUCCESS` already existed;
   failed logins and lockouts were only going to the console logger, never to the Audit Logs page.
   Added `auditService.logAction(...)` calls at the three failure points. No existing control flow,
   return values, or thrown exceptions were changed — only new log lines.

2. **No-store cache headers for dashboard pages** (`firebase.json`)
   Report requires "Browser Back Button Must Not Reopen Protected Data." Added
   `Cache-Control: no-store` headers for `admin-dashboard.html` and `teacher-dashboard.html`.
   Pure hosting-config addition; no frontend/backend code touched.

## Already implemented (verified, no action needed)

- Unique DB constraint on `(student_id, attendance_date, session)` — already in both the Flyway
  migration and the entity `@UniqueConstraint`.
- Login page: double-submit prevention, disabled button during submit, and a
  "Starting secure server and signing you in..." message on slow load — all already in `login.js`.
- Department/Batch soft-delete via active/inactive flag (not hard delete) — already implemented.
- Audit logging for student/teacher/department/batch create-edit-deactivate, role changes, and
  session lock/unlock — already implemented in `AdminService`.
- PDF export for reports — already implemented via jsPDF in `admin.js`.

## Found but NOT touched — these need actual behavior/schema changes, not pure additions

1. **CSV student import is completely missing.** No endpoint, service method, or frontend UI exists
   anywhere in the repo, despite being a P1 item in the report (upload → validate → preview →
   duplicate detection → batch insert → import summary). This is a new feature, not a patch.

2. **Teacher department scoping is a stub, not a bug fix.** In `TeacherController.java`
   (`getDepartmentAttendance` and one other endpoint), `departmentId` is hardcoded to `null` instead
   of being derived from the logged-in teacher — meaning a teacher's attendance queries aren't scoped
   to their own department at all right now. Fixing this properly needs a `department` field added to
   the `Teacher` entity (there currently isn't one), a migration, and a controller change — real code
   changes, so I left it alone per your instruction.

3. **Session unlock has no "require reason" step.** The report calls for a mandatory reason on unlock;
   the current `unlockSession` service method takes no reason parameter and doesn't store one.

Want me to build any of these three as a proper follow-up (starting with #2, since it's a real
authorization gap)?
