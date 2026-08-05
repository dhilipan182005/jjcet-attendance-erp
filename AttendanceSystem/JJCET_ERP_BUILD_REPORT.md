# JJCET ERP — Build Report

Date: 11 July 2026
Scope: Full implementation pass against JJCET_ERP_MASTER_PROMPT.md, working directly in the
existing repository (not a rewrite).

## How to verify this yourself (I could not run these here)

This sandbox has no network access to Maven Central, so `./mvnw clean package` / `./mvnw clean test`
could not actually be executed here. Every Java file touched was hand-reviewed and passed a
brace/parenthesis balance check, but that is not the same as a real compile. **Run these before
deploying:**

```
./mvnw clean test
./mvnw clean package
```

If either fails, the most likely culprits are listed under "Risk areas" at the bottom of this
report - check those files first.

---

## What actually changed, section by section

### Self-correction: a bug from the previous session, found and fixed this session
Removing `WebSocketConfig.java` last session (dead STOMP broker, confirmed zero real usage) left
`AdminService` still injecting `SimpMessagingTemplate` to broadcast session open/close events that
nothing ever consumed. That field/class would not have existed on the classpath at all once the
`spring-boot-starter-websocket` dependency was removed - this would have been a **compilation
failure**, not just a runtime one. Fixed by removing the two dead broadcast calls rather than
re-adding the whole STOMP stack for a feature with no listener. **PASS** (verified no remaining
`SimpMessagingTemplate`/`WebSocket` references anywhere in `src/`).

### Section 4 — Two-role model
- `Role` enum reduced to `ADMIN`, `TEACHER`.
- `V2__two_role_model_and_registration_domain.sql` migrates existing data:
  `SUPER_ADMIN → ADMIN`, `ATTENDANCE_MANAGER → TEACHER`. `audit_logs.role` is intentionally left
  untouched for historical accuracy, per the master prompt.
- **Decision record (required by the prompt):** `ATTENDANCE_MANAGER` maps to `TEACHER`, not
  `ADMIN`. Verified via `SecurityConfig` (it was only ever granted the same endpoints as
  `TEACHER` - never any `/api/admin/**` route) and `auth.js` (its post-login redirect already sent
  `ATTENDANCE_MANAGER` to the teacher dashboard, identically to `TEACHER`).
- `AdminBootstrap` now creates the bootstrap account as `ADMIN`. The old "protect the bootstrap
  SUPER_ADMIN by user ID" logic in `AdminService` (role assignment, teacher deletion) was replaced
  with a general `countByRoleAndActiveTrue(ADMIN) <= 1` check, which is what the prompt actually
  asks for ("never reach zero active Admins") rather than protecting one hardcoded account.
- All frontend role literals updated (`auth.js` redirect, `admin.js` guards and the Admin-only
  "Role" button, `teacher.js` guard, the role-assignment dropdown in `admin-dashboard.html`).
- **Real bug found and fixed along the way:** the "Access Type" dropdown on the Register Teacher
  form was sent to the backend but silently dropped - `RegisterTeacherRequest` had no `accessType`
  field, and the service hardcoded `Role.TEACHER` for every new account. This meant **there was no
  way to create a new Admin account at all** outside the bootstrap environment variables. Added the
  field (validated to `ADMIN|TEACHER`) and wired it through.
- **Real bug found and fixed:** `deleteTeacher` (the "Archive" action) deactivated the `Teacher` row
  but never the underlying `User` row, so an "archived" teacher's login account stayed active. Fixed
  to deactivate both, matching what `restoreTeacher` already correctly did in reverse.
- Grep-verified: zero remaining `SUPER_ADMIN`/`ATTENDANCE_MANAGER` references anywhere in `src/` or
  `frontend/` outside one explanatory code comment. **PASS**.
- **NOT TESTED:** an actual login as a migrated `SUPER_ADMIN`/`ATTENDANCE_MANAGER` account against a
  real database - I have no database to run the migration against here.

### Sections 5-7 — Access control and User Management
- `TeacherController`'s two `departmentId = null` stubs (the real authorization gap flagged in
  `GAPS_REPORT.md`) are fixed - both now derive the department from the logged-in teacher's own
  `Teacher.department` relation, added to the entity this session. A teacher with no department
  assigned yet gets an empty result with a clear message, not every department's data.
- Added a Department field to both the Register Teacher form and the Edit Teacher modal, and wired
  it through `AdminService.createTeacher`/`updateTeacher`. This also incidentally fixed a second,
  pre-existing dead reference (`document.getElementById('t-dept')` was already being populated by
  `admin.js` even though no such element existed in the HTML until now).
- Last-active-Admin protection implemented in `assignRole` and `deleteTeacher` (see above).
- **PARTIAL:** self-demotion prevention is covered by the same role-count check, but I did not
  build a dedicated "Users" list/search/filter screen exactly as sketched in Section 7's workflow -
  user management currently still lives inside the existing Register/Edit Teacher screens rather
  than a unified Users page. Functionally equivalent, not identical to the spec's wireframe.
- **NOT TESTED:** actually attempting to demote/delete the last Admin end-to-end against a live
  database and confirming the 403.

### Sections 10-14 — Registration Number domain model
- New `RegistrationNumberService`: `normalize`, `validate`, `parseCollegeCode`,
  `parseAdmissionYearCode`, `resolveAdmissionYear`, `parseDepartmentCode`,
  `parseStudentSerialNumber`, `deriveExpectedBatch`, `validateCollegeCode`,
  `validateDepartmentConsistency`, `validateBatchConsistency`, plus a single `parse()` entry point
  used by CSV import. College code and course duration are configurable
  (`institution.college-code`, `institution.engineering-course-duration-years`), not hardcoded.
- Traced the worked example from the prompt by hand: `811323106013` → college `8113`, admission
  year `2023`, department `106`, serial `013`, expected batch `2023-2027`. Matches.
- **PASS for logic review. NOT TESTED as an actual JUnit run** - no test file was added for this
  class in this pass (see "Still missing" below) and I could not run `mvn test` here regardless.

### Section 8/9 — Batch structure and CSV import
- `Department` gained `departmentCode`; `Batch` gained `department`, `startYear`, `endYear`;
  `Teacher` gained `department`. All nullable/additive - no existing row is broken, unique
  constraints on the new columns are partial indexes (`WHERE ... IS NOT NULL`) so nothing already
  in the database is forced to comply retroactively.
- Built `CsvImportService` + `CsvImportController` (`/api/admin/students/csv/template|preview|confirm`)
  from scratch - this did not exist anywhere before this session. Preview never writes to the
  database; confirm re-parses and re-validates the same uploaded file server-side rather than
  trusting a client-supplied preview payload, runs inside one `@Transactional` method (full
  rollback on failure), and writes `CSV_IMPORT_STARTED/COMPLETED/FAILED`,
  `STUDENT_CREATED_BY_CSV/UPDATED_BY_CSV`, `DEPARTMENT_CREATED_BY_CSV`, `BATCH_CREATED_BY_CSV` audit
  entries. New department/batch name-vs-code conflicts are rejected per Section 13. A CSV row whose
  department doesn't match the registration number's encoded department is rejected outright
  (Section 16), and an optional `batch_name` column is a consistency check only, never a trusted
  override (Section 17).
- Idempotency relies on the natural behavior of the upsert-and-compare logic (unchanged rows
  produce zero writes) rather than a separate cached-result short-circuit, which is simpler and
  can't go stale.
- CSV parsing has no external dependency (hand-written, RFC4180-style quoted-field splitting) -
  deliberately avoided adding a new Maven dependency I could not verify resolves, given no Maven
  Central access here.
- Frontend: new "Import Students" page (template download, file picker, Check File → preview
  summary + per-row table, Import Students → confirm), added to the sidebar and route map without
  altering any existing page's layout.
- **PASS for logic review of every rule in Sections 10-22. NOT TESTED end-to-end** - no database to
  actually upload a CSV against.
- **NOT TESTED:** the idempotent-reimport claim specifically (re-uploading the same file twice and
  confirming 0 new / 0 updated) - reasoned through, not executed.

### Section 23-24 — Historical attendance integrity and teacher edit workflow
- Added nullable `department_id`/`batch_id` snapshot columns to `attendance` (migration only this
  pass - **not yet wired into `AttendanceService` to actually populate them on write**, see "Still
  missing" below). This is a real, only-partially-closed gap: the columns exist so a later pass can
  populate them, but new attendance rows created right now still don't get a department/batch
  snapshot written to them.
- Teacher attendance edit already had lock-status enforcement server-side (verified, not just a
  hidden button) from the earlier QA pass in this conversation - unchanged this session.
- **PARTIAL** on Section 23 specifically - schema exists, write path does not yet.

### Section 8/25/26 — Simple text and human-readable dates
- New centralized `date-utils.js` (`formatDate`, `formatDateTime`, `formatRelative`,
  `formatUpdated`, `formatStarted`) - handles null, invalid, future, and date-only values without
  timezone shifting, exactly per Section 9's requirements.
- **Real duplicate found and removed:** `utils.js` already had its own `formatDate()`
  (`date.toLocaleDateString()` with no options → renders like `7/10/2026`, not human-readable, and
  was never actually imported/used anywhere - dead code). Removed it in favor of the new module,
  which is exactly the "duplicate date formatting functions" anti-pattern the prompt calls out by
  name.
- Applied the new utility to the Audit Logs table, which was previously showing a raw
  `2026-07-10 14:32:16`-style substring straight from the API response - now shows
  `10 July 2026, 2:32 PM`.
- **PARTIAL, and I want to be direct about the size of what's left:** roughly 15 other inline
  `toLocaleDateString()`/`toLocaleString()` call sites across `admin.js` and `teacher.js` were
  **not** migrated to the new utility in this pass. I made a real, working, centralized utility and
  used it in the highest-visibility place (Audit Logs), but claiming the rest of the app now shows
  human-readable dates everywhere would not be true. This is the single largest piece of "Section 9
  work" still remaining.
- No systematic pass was made through Sections 8/25 ("simple text audit" across every screen) - only
  the specific strings the master prompt called out as examples were checked/fixed opportunistically
  (the role dropdown, the unlock reason prompts). A full audit of every label/button/error string in
  a codebase this size was not attempted in this session.

### QA fixes carried in from earlier in this conversation, included in this build
- `AttendanceService.detectActiveSession()` now prefers a genuine time-window match over an
  admin-unlocked session, so an admin unlocking an earlier session for backfill can't hijack
  barcode auto-detection during a different, currently-live session.
- `JwtFilter` now catches `UsernameNotFoundException` (a still-valid JWT for a since-deactivated
  user) and continues unauthenticated instead of throwing an uncaught raw 500.
- `RateLimitFilter` used `getRemoteAddr()`, which returns Render's proxy address for every request -
  meaning every user on the platform shared one 50-req/min bucket. Extracted a shared
  `ClientIpResolver` (also now used by `AuditService`, removing a second, separate copy of the same
  logic) that correctly reads `X-Forwarded-For`.
- Session unlock now requires a non-blank reason (Section 18/24 requirement), persisted on
  `AttendanceUnlock.reason` and included in the audit log message. Frontend prompts for it and
  blocks the request if left blank.
- Fixed a genuinely broken frontend function while wiring the reason prompt in:
  `window.applyLockOverride` was defined **twice** in `admin.js`; the second definition silently
  overwrote the first, and the surviving one posted a query string that didn't match the backend's
  `@RequestBody` contract at all - so session lock/unlock likely didn't work before this fix,
  regardless of the reason requirement.
- Application renamed to "JJCET ERP" consistently across every page title, the login heading, the
  logo alt text, and the README.

---

## Still missing / explicitly deferred (do not assume these are done)

1. **Attendance historical snapshot is schema-only.** `AttendanceService` was not modified to
   populate the new `attendance.department_id`/`batch_id` columns on write. Section 23 is not
   closed until that's done.
2. **~15 date-formatting call sites not yet migrated** to `date-utils.js` (see above).
3. **No unified "Users" management screen** - Section 7's exact workflow (search/filter users, one
   Add User flow choosing Admin or Teacher) is functionally covered by the existing Register/Edit
   Teacher screens plus the Role modal, but not built as the single dedicated page the prompt
   sketches.
4. **No automated tests were added** for `RegistrationNumberService`, `CsvImportService`, or the
   role migration, despite Section 28 requiring them. Every rule was reasoned through by hand and
   cross-checked against the entity/repository code, which is not a substitute for `./mvnw test`
   actually passing.
5. **Registration-number correction workflow (Section 18) and batch correction workflow are not
   built** - both are explicitly separate, controlled, reason-required Admin flows the prompt asks
   for, distinct from the CSV path. Not attempted this session.
6. **A full Section 8/25 text audit was not performed.** Assume most of the app's copy is unchanged
   from before this session except where explicitly noted above.
7. **PWA manifest** - Section 3 mentions one, but no `manifest.json` exists anywhere in this
   repository, so there was nothing to rename.

## Risk areas to check first if the build fails

- `CsvImportService`/`CsvImportController` are the largest new surface area and the most likely
  place for a real compile error I didn't catch by eye (multipart file handling, generic type
  inference on the builders).
- `Batch`/`Department`/`Teacher` entity changes ripple into any existing code that builds these via
  `@Builder` with positional assumptions - I grepped for every `.builder()` call site I could find
  and none appeared to rely on field order, but this is worth double-checking if `AdminService`
  fails to compile.
- The V2 migration assumes PostgreSQL syntax (`GENERATED BY DEFAULT AS IDENTITY`, partial unique
  indexes) matching V1's style - not tested against a real Postgres instance.
