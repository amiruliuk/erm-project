# Changes in v2 (review of ERM-MBPLC-v1)

## Security fixes
- Removed the hard-coded `admin / ChangeMe123!` account (it was still created on every start). First-run admin now uses `ERM_ADMIN_PASSWORD` or a one-time random password.
- `DemoDataInitializer` is limited to the `demo` profile again (the annotation had been commented out).
- Any signed-in employee could create/edit/delete departments, and verify anyone's credentials. URL rules added; credential verification is ADMIN only (matches the UI).
- Inline `<script>` / `onsubmit` were blocked by the Content-Security-Policy: the department delete confirmation never appeared and the credential form toggle never ran. Moved to `app.js` / `data-confirm`.
- Uploads are now identified by file signature (not the client Content-Type) and served with a type derived from the stored extension.
- Credential documents are served only to the owner and staff roles, and each view is audited.
- Default upload dir `/var/data/erm/employees` made the app fail to start on most machines; now `./data/uploads`.
- DB passwords come from environment variables; error messages no longer exposed.

## Missing features added
- Credential document download (the "View" link returned 404), credentials report with expiry, Credentials and Departments in the menu.
- Approval requests can now be raised (there was no way to create one).
- Tasks: due date, estimated hours, status updates (assignee or manager), overdue flag.
- RACI: one Accountable per activity, no duplicate person per activity, remove entry.
- User access page (create logins, role, enable/disable, employee link, password reset) and self-service password change with password policy.
- Employee employment-status change (only ACTIVE employees are allocated/recommended).
- Audit entries for skills, RACI, tasks, users, documents.
- Tests (`ErmSecurityTests`), `spring-security-test` dependency.

## Fixed
- Dashboard "open work" counted all tasks.
- Department messages never displayed (wrong flash attribute names).
- Lazy-loading risk when verifying credentials (new `findWithEmployeeById`).


# Changes in v3 (integration and entity-relationship review of v2)

## Security / correctness
- `@EnableMethodSecurity` added: the `@PreAuthorize` rules on the governance controller were silently ignored before.
- `/governance/**` writes are now ADMIN / MANAGER only (read-only AUDITOR could previously POST). Evidence verification stays open to ADMIN / APPROVER / AUDITOR; a recorder still cannot verify their own evidence.
- `DataInitializer`: demo users (`Demo@12345`) and demo data are created only under the `demo` profile again; `demo` + `postgres|mysql|prod` aborts start-up; the first-run admin (`ERM_ADMIN_PASSWORD` or a one-time random password in the log) is restored. The CBS seed is demo-only.
- `@EnableScheduling` added (the review reminder job never ran). Reminders are no longer duplicated daily, and open cross-training plans past target date become OVERDUE with a notification to the owner.
- Evidence files now use the shared `FileStorageService` (same upload folder as photos/credentials, PDF/JPEG/PNG by file signature, generated names). v2 read a non-existent `app.upload.dir` key and wrote to `./uploads`. **If you already uploaded evidence with v2, move the files from `./uploads` to `./data/uploads`.**

## Missing pieces added
- `GET /evidence/{id}/download` (audited) - the paperclip links returned 404.
- Roster page: form to assign primary / backups (distinct, active, critical skill only); readiness-test form and history pages; skill-matrix CSV export.
- Validation messages are shown on the page instead of a blank error.

## Entity model
- Single evidence model for governance: `BackupReadinessTest` lost its four duplicate file columns, `BackupRoster` lost its redundant `evidence` FK (reach it through `lastTest.evidence`). With `ddl-auto=update` the old columns simply stay unused; drop them manually if you want.
- Review minutes are now linked to their `ReviewSchedule` (the Evidence row was created and dropped before).
- `lastTest` is only moved forward, so a back-dated test cannot replace a newer one.
- Fetch graphs added so roster / history pages do not hit `LazyInitializationException` (open-in-view is off).
- Skill matrix scale is 0-5, matching `EmployeeSkill` (1-5) and the assessment form; the old page said 0-4.

## Removed
- `DataInitializer_bk.java` (fully commented-out copy).

## Known, deliberately not changed
- `AppUser.employeeCode` is still a string link, not an FK to `Employee`.
- Project / task / approval statuses and `Employee.employmentStatus` are still free strings (enums would need a data migration).
- No delete flow for skills / employees; they would fail on foreign keys if added without guards.
- This build was reviewed by reading and a syntax parse only; run `mvn clean verify` before deploying.

## Update: single-runner seeder
- `DataInitializer.java` is now ONE file with ONE runner (`seedEverything`): base data -> demo data -> demo links -> first-run admin (non-demo) -> CBS-GOV-003 governance data (demo). The separate `seedCbsGovernance` bean and `@Order` annotations are gone. Load test data with `-Dspring-boot.run.profiles=demo`.

## Update: team-wise reports
- New reports under **Reports & analytics** (team = department; pick one team or all): `team-summary`, `team-raci`, `team-roster`, `team-plans`. Same table / CSV / print page as the other reports. Service: `TeamReportService`; `ReportController` takes an optional `team` parameter.


# Changes in v4 (units inside departments + review)

## Units (department > unit)
- New `Unit` entity (`units` table): belongs to one department, optional unit head, active flag. Name is unique inside its department, code is unique overall.
- `Employee.unit` and `Project.owningUnit`. Tasks, allocations, RACI, backup roster, cross-training plans and credentials follow the employee's unit, so everything can be viewed unit-wise without extra columns.
- **Units** menu: list (filter by department), create / edit / delete (delete is refused while people or projects are still attached), and a **unit workspace** page (`/units/{id}`) with KPIs and tables for members, projects, open tasks, allocations, RACI, roster coverage, training plans and credentials needing attention.
- Employee screen: pick department then unit when creating; admins can move an employee (`POST /employees/{id}/assign`). A unit must belong to the chosen department (checked on the server; moving someone out of a department also clears them as head of a unit there).
- Project screen: owning unit. Unit filter on Employees, Projects, Tasks, Resource planning, RACI and Intelligent assignment (`?unitId=`).
- Team reports are now per unit (`team-summary`, `team-raci`, `team-roster`, `team-plans`), with Department and Unit filters. CBS skill matrix columns are grouped by "Department / Unit".
- Demo seed: 15 demo units with heads and members; demo projects are owned by units.
- Security: `/units/**` read = ADMIN / MANAGER / APPROVER / AUDITOR, write = ADMIN / MANAGER.

## Database
- `ddl-auto=validate` is on, so run `sql/V4__add_units.sql` once before starting v4. Optional `sql/V4b__optional_backfill_default_units.sql` puts existing people and projects into a "General" unit per department.

## Security / hygiene
- The DB password and the default first-run admin password (`MBLmbl123456`) were hard-coded in `application.properties`. They now come from `ERM_DB_URL`, `ERM_DB_USER`, `ERM_DB_PASSWORD`, `ERM_ADMIN_PASSWORD` or from a git-ignored `./local.properties` (`local.properties.example` shows the format). **Rotate both passwords: they were in the zip you shared.**

## Not changed (see review notes)
- Not compiled here: the sandbox has no Maven / Spring jars. The sources were syntax-checked with javac only. Run `mvn clean verify` and click through the Units pages before deploying.
- Data-level scoping (a manager seeing only their own unit) is not implemented; units are a filter and a grouping, not yet a permission boundary.

