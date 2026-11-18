# NEXUS ERM — Enterprise Project & Resource Management

A Java 21 / Spring Boot 3.5 / Maven starter application for enterprise project delivery, people, skills, RACI, work assignment, resource allocation, approvals, reporting and audit.

## Requirements
- JDK 21
- Maven 3.9+
- Optional PostgreSQL or MySQL for production

## Run locally
```bash
mvn clean spring-boot:run
```
Open http://localhost:8080

### First sign-in
There is **no built-in default password**. On the first start with an empty database the app creates one `admin` login:
- set `ERM_ADMIN_PASSWORD` (min. 12 characters) before the first start, **or**
- leave it unset and copy the random password printed once in the start-up log.

Then open **User access** to create real logins and link each one to an employee record, and change the admin password under **My account**.

### Demo data (demo / test only)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```
The `demo` profile seeds a bank-themed data set and these logins (password `Demo@12345`):

| Login | Role | What it can do |
|---|---|---|
| `admin` | ADMIN | Manage people, logins and all data, verify credentials, view audit log. **Cannot approve.** |
| `pmo.manager` | MANAGER | Projects, allocation, tasks, skills, RACI, recommendations, reports, raise approval requests |
| `dept.head` | APPROVER | The only role that can approve / reject. Cannot approve own requests. |
| `auditor` | AUDITOR | Read-only everywhere, including reports and audit log |
| `employee1` | USER | Own tasks (can update their status), skills, RACI entries and credentials, plus the project list |

The demo seed (users, projects, CBS governance data) only runs under the `demo` profile and the app refuses to start if `demo` is combined with `postgres`, `mysql` or `prod`.
If you ran an older version, delete the `data/` folder once so the seed data is recreated cleanly.
The H2 console is disabled (`spring.h2.console.enabled=false`).

## Role-based access
Enforced in `SecurityConfig` (URL level) and `HomeController` (data level: maker-checker, own-records-only).
Every state-changing request, sign-in success/failure and denied access is written to the audit log (`/audit`).
Passwords: minimum 12 characters with upper-case, lower-case and a digit (login administration and self-service change). Not yet implemented: account lockout, MFA, LDAP group-to-role mapping, immediate session invalidation when a login is disabled or its role changes (the 15-minute idle timeout applies).

## Database profiles
H2 is the default. For PostgreSQL:
1. Create database `erm` and user `erm`.
2. Update `application-postgres.yml` credentials.
3. Run `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`.

For MySQL:
1. Create database `erm` and user `erm`.
2. Update `application-mysql.yml` credentials.
3. Run `mvn spring-boot:run -Dspring-boot.run.profiles=mysql`.

Database credentials come from `ERM_DB_URL`, `ERM_DB_USER`, `ERM_DB_PASSWORD`. For production, replace `ddl-auto: update` with Flyway/Liquibase migrations, configure TLS, secrets, backups and database least-privilege accounts.

## Active Directory / LDAP
LDAP is optional and disabled by default. Configure `app.security.ldap` in `application.yml`:
```yaml
app:
  security:
    ldap:
      enabled: true
      url: ldaps://ad.example.com:636
      base: dc=example,dc=com
      user-dn-pattern: (userPrincipalName={0})
```
The current starter uses a simple LDAP user-DN pattern. Many Active Directory environments require a search-based bind configuration, group-to-role mapping, LDAPS truststore configuration and domain-specific testing. Implement those before production rollout. Local database authentication remains configured as a fallback provider.

## Modules included
- Employee, department and unit register (a department has units; people and projects belong to a unit; each unit has a workspace page)
- Project register
- Skill catalogue and employee proficiency assessment
- Resource recommendation ranking (70% skill fit, 30% estimated capacity)
- Work item / job assignment
- RACI register
- Approval decision screen
- Portfolio snapshot reporting
- Audit log entity and service foundation
- Notification entity foundation
- CBS-GOV-003 governance: skill matrix (CSV export), backup roster, readiness tests with history, cross-training plans, six-monthly reviews, evidence register with verified downloads
- Credentials register: degrees, training, certifications, document upload (type checked by file signature), verification workflow with maker-checker, expiry report
- Approval requests: raised by managers, decided by approvers
- User access administration and password change
- Spring Security form login
- H2 / PostgreSQL / MySQL configuration profiles

## Important implementation notes
This is a runnable foundation, not a fully validated enterprise production release. The first iteration provides the core domain and working screens. Before production, complete: edit/delete for projects, tasks and employees, approval routing and notification delivery (the `Notification` entity is not used by any screen yet), pagination, XLSX/PDF exports, database migrations (Flyway/Liquibase), broader automated tests, account lockout and MFA. RACI enforces one Accountable person per activity and one role per person per activity; it does not yet require at least one Responsible. The recommender is a transparent heuristic and must be reviewed by a manager before assignment.

## Monday.com-inspired workspace UI update

The web interface now uses a modern work-management layout inspired by common visual patterns in platforms such as monday.com: a persistent workspace sidebar, workspace context header, project/task board tables, compact status labels, search filtering, and responsive page layout. It is an original NEXUS ERM design and does not use monday.com branding or proprietary assets.

The board search controls filter the rows already rendered on the current page. They do not perform server-side/global search. Existing project and task creation routes are preserved.

For an isolated bank intranet, host Bootstrap and Bootstrap Icons locally under `src/main/resources/static/vendor/` and update the stylesheet references in `templates/fragments/layout.html`; the starter currently references public CDN assets.


## Configuration
Secrets are not kept in `application.properties`. Use the variables below or a git-ignored `./local.properties` (copy `local.properties.example`).
For an existing database run `sql/V4__add_units.sql` once (the app validates the schema and will not start without the new table).

| Variable | Purpose |
|---|---|
| `ERM_ADMIN_PASSWORD` | First-run admin password (min. 12 chars) |
| `ERM_UPLOAD_DIR` | Folder for photos and credential documents (default `./data/uploads`) — back it up |
| `ERM_COOKIE_SECURE` | Set `true` when served over HTTPS |
| `ERM_DB_URL`, `ERM_DB_USER`, `ERM_DB_PASSWORD` | PostgreSQL / MySQL profiles |

## Tests
No automated tests are included in this archive (the `src/test` folder, including `ErmSecurityTests`, was not part of the v2 zip). Add them back before relying on `mvn test`.

## UI theme
The workspace uses a purple, red and white enterprise theme. Main navigation is deep purple, primary actions are purple, important/critical accents are red, and content surfaces remain white with a soft lavender page background.
