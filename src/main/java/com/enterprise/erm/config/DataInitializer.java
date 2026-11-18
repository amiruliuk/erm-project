package com.enterprise.erm.config;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import com.enterprise.erm.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * ONE-FILE data initializer with ONE runner ({@code seedEverything}). Meant for local testing.
 *
 * <p>Order inside the runner:
 * <ol>
 *   <li>base data on an empty database (department, EMP001, basic skills) - every profile;</li>
 *   <li>demo data - {@code demo} profile only (users, employees, skills, projects, allocations, work items,
 *       RACI, approvals, notifications, audit);</li>
 *   <li>login-to-employee links - {@code demo} only;</li>
 *   <li>first-run admin ({@code ERM_ADMIN_PASSWORD} or a one-time random password) - non-demo only;</li>
 *   <li>CBS-GOV-003 governance data - {@code demo} only (roster, readiness tests, cross-training plans,
 *       six-month review, evidence).</li>
 * </ol>
 *
 * Safe to re-run: every block guards on {@code count() == 0} and every helper is idempotent.
 * Load the test data with: {@code mvn spring-boot:run -Dspring-boot.run.profiles=demo}</p>
 */
@Slf4j
@Configuration
public class DataInitializer {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final String DEMO_PASSWORD = "Demo@12345"; // demo logins only

    // =====================================================================
    // 1) base + demo seed
    // =====================================================================

    @Bean
    CommandLineRunner seedEverything(AppUserRepository users, DepartmentRepository depts, UnitRepository unitRepo, EmployeeRepository employees,
                                     SkillRepository skills, EmployeeSkillRepository employeeSkills,
                                     ProjectRepository projects, ResourceAllocationRepository allocations,
                                     WorkItemRepository workItems, RaciEntryRepository raci,
                                     ApprovalRepository approvals, NotificationRepository notifications,
                                     BackupRosterRepository rosters, BackupReadinessTestRepository tests,
                                     CrossTrainingPlanRepository plans, ReviewScheduleRepository reviews,
                                     EvidenceRepository evidence,
                                     AuditService audit, PasswordEncoder encoder, Environment env,
                                     @Value("${app.bootstrap.admin-password:}") String configuredPassword) {
        return args -> {
            LocalDate today = LocalDate.now();

            boolean demo = env.acceptsProfiles(Profiles.of("demo"));
            if (demo && env.acceptsProfiles(Profiles.of("postgres", "mysql", "prod"))) {
                throw new IllegalStateException(
                        "The demo profile (shared demo passwords) must not run together with postgres, mysql or prod.");
            }

            // ---------------------------------------------------------- 1a) base data (fresh install)
            if (depts.count() == 0) {
                var d = new Department();
                d.setName("Information Technology");
                d.setCode("IT");
                depts.save(d);

                var e = new Employee();
                e.setEmployeeCode("EMP001");
                e.setFullName("System Administrator");
                e.setEmail("admin@example.com");
                e.setJobTitle("Administrator");
                e.setDepartment(d);
                employees.save(e);

                for (String n : new String[]{"Java", "Spring Boot", "SQL", "Project Management", "Business Analysis"}) {
                    var s = new Skill();
                    s.setName(n);
                    s.setCategory("Technical");
                    skills.save(s);
                }
            }

            // ---------------------------------------------------------- 1b) demo data (only once)
            if (demo && projects.count() == 0) {

                // ---- app users
                user(users, encoder, "admin", "ROLE_ADMIN");
                user(users, encoder, "pmo.manager", "ROLE_MANAGER");
                user(users, encoder, "dept.head", "ROLE_APPROVER");
                user(users, encoder, "auditor", "ROLE_AUDITOR");
                user(users, encoder, "employee1", "ROLE_USER");

                // ---- departments
                Map<String, Department> d = new HashMap<>();
                for (String[] x : new String[][]{
                        {"Information Technology", "IT", "Core banking, infrastructure, applications"},
                        {"Retail Banking", "RB", "Branches and retail products"},
                        {"Corporate Banking", "CB", "Corporate lending and relationship management"},
                        {"Risk & Compliance", "RC", "Credit risk, AML/CFT, regulatory compliance"},
                        {"Operations", "OPS", "Back office, settlement, remittance"},
                        {"Information Security", "ISEC", "Cyber security and IT governance"},
                        {"Internal Audit", "IA", "Independent assurance"},
                        {"Human Resources", "HR", "People and training"}}) {
                    Department dep = depts.findAll().stream()
                            .filter(o -> o.getCode() != null && o.getCode().equals(x[1]))
                            .findFirst().orElseGet(Department::new);
                    dep.setName(x[0]);
                    dep.setCode(x[1]);
                    dep.setDescription(x[2]);
                    d.put(x[1], depts.save(dep));
                }

                // ---- skills (keep existing, add bank ones)
                Map<String, Skill> s = new HashMap<>();
                skills.findAll().forEach(k -> s.put(k.getName(), k));
                for (String[] x : new String[][]{
                        {"Java", "Technical"}, {"Spring Boot", "Technical"}, {"SQL", "Technical"},
                        {"Project Management", "Technical"}, {"Business Analysis", "Technical"},
                        {"Core Banking (T24/Finacle)", "Banking"}, {"AML / CFT", "Compliance"},
                        {"Credit Risk Analysis", "Risk"}, {"Cyber Security", "Technical"},
                        {"Payments & SWIFT", "Banking"}, {"Regulatory Reporting", "Compliance"},
                        {"Internal Audit", "Audit"}, {"Data Analytics", "Technical"}}) {
                    if (!s.containsKey(x[0])) {
                        Skill k = new Skill();
                        k.setName(x[0]);
                        k.setCategory(x[1]);
                        s.put(x[0], skills.save(k));
                    }
                }

                // ---- CBS-GOV-003 critical skills (flag critical = true)
                for (String[] x : new String[][]{
                        {"CBS Application Configuration",                      "Banking"},
                        {"CBS Release and Deployment",                         "Banking"},
                        {"EOD / Batch Processing",                             "Banking"},
                        {"Interface and Integration (payments, ATM, SWIFT)",   "Banking"},
                        {"Application Server / Middleware",                    "Technical"},
                        {"Database Basics (queries, reconciliation)",          "Technical"},
                        {"Incident Triage and RCA",                            "Operations"},
                        {"Change Management",                                  "Operations"},
                        {"DR Failover and Recovery",                           "Operations"},
                        {"Vendor Coordination",                                "Operations"}}) {
                    Skill k = s.get(x[0]);
                    if (k == null) {
                        k = new Skill();
                        k.setName(x[0]);
                        k.setCategory(x[1]);
                        skills.save(k);
                        s.put(x[0], k);
                    }
                    k.setCritical(true);
                    skills.save(k);
                }

                // ---- employees
                Map<String, Employee> e = new HashMap<>();
                for (String[] x : new String[][]{
                        {"EMP101", "Rahim Uddin",     "rahim.uddin@demo-bank.example",     "Head of IT Projects",           "IT",   "01700000101"},
                        {"EMP102", "Nusrat Jahan",    "nusrat.jahan@demo-bank.example",    "Senior Java Developer",         "IT",   "01700000102"},
                        {"EMP103", "Tanvir Ahmed",    "tanvir.ahmed@demo-bank.example",    "Core Banking Specialist",       "IT",   "01700000103"},
                        {"EMP104", "Farhana Akter",   "farhana.akter@demo-bank.example",   "Business Analyst",              "RB",   "01700000104"},
                        {"EMP105", "Imran Hossain",   "imran.hossain@demo-bank.example",   "Head of Retail Banking",        "RB",   "01700000105"},
                        {"EMP106", "Sadia Rahman",    "sadia.rahman@demo-bank.example",    "Corporate Relationship Manager","CB",   "01700000106"},
                        {"EMP107", "Kamal Hasan",     "kamal.hasan@demo-bank.example",     "Chief Compliance Officer",      "RC",   "01700000107"},
                        {"EMP108", "Mitu Sultana",    "mitu.sultana@demo-bank.example",    "AML Analyst",                   "RC",   "01700000108"},
                        {"EMP109", "Arif Chowdhury",  "arif.chowdhury@demo-bank.example",  "Operations Manager",            "OPS",  "01700000109"},
                        {"EMP110", "Shirin Begum",    "shirin.begum@demo-bank.example",    "Remittance Officer",            "OPS",  "01700000110"},
                        {"EMP111", "Zahid Karim",     "zahid.karim@demo-bank.example",     "Information Security Officer",  "ISEC", "01700000111"},
                        {"EMP112", "Rubina Islam",    "rubina.islam@demo-bank.example",    "Internal Auditor",              "IA",   "01700000112"},
                        {"EMP113", "Habib Rahman",    "habib.rahman@demo-bank.example",    "HR Manager",                    "HR",   "01700000113"},
                        {"EMP114", "Lamia Khan",      "lamia.khan@demo-bank.example",      "Data Analyst",                  "IT",   "01700000114"},
                        {"EMP116", "Farid Uddin",     "farid.uddin@demo-bank.example",     "Middleware Engineer",           "IT",   "01700000116"},
                        {"EMP117", "Sharmin Akter",   "sharmin.akter@demo-bank.example",   "Database Administrator",        "IT",   "01700000117"},
                        {"EMP118", "Jahid Hasan",     "jahid.hasan@demo-bank.example",     "Change Manager",                "OPS",  "01700000118"},
                        {"EMP119", "Nadia Sultana",   "nadia.sultana@demo-bank.example",   "Payments Specialist",           "OPS",  "01700000119"},
                        {"EMP120", "Rashed Khan",     "rashed.khan@demo-bank.example",     "DR Coordinator",                "ISEC", "01700000120"},
                        {"EMP121", "Tahmina Begum",   "tahmina.begum@demo-bank.example",   "Vendor Manager",                "OPS",  "01700000121"},
                        {"EMP122", "Sohel Rana",      "sohel.rana@demo-bank.example",      "Release Engineer",              "IT",   "01700000122"},
                        {"EMP123", "Munira Haque",    "munira.haque@demo-bank.example",    "Incident Manager",              "OPS",  "01700000123"}}) {
                    Employee emp = employees.findByEmail(x[2]).orElseGet(Employee::new);
                    emp.setEmployeeCode(x[0]);
                    emp.setFullName(x[1]);
                    emp.setEmail(x[2]);
                    emp.setJobTitle(x[3]);
                    emp.setDepartment(d.get(x[4]));
                    emp.setPhone(x[5]);
                    emp.setEmploymentStatus("ACTIVE");
                    e.put(x[0], employees.save(emp));
                }
                Employee left = new Employee();
                left.setEmployeeCode("EMP115");
                left.setFullName("Former Employee");
                left.setEmail("former@demo-bank.example");
                left.setJobTitle("Developer");
                left.setDepartment(d.get("IT"));
                left.setEmploymentStatus("INACTIVE");
                employees.save(left);

                // ---- units inside departments (unit code, name, department code, head employee code, members)
                Map<String, Unit> u = new HashMap<>();
                for (String[] x : new String[][]{
                        {"IT-PMO",   "IT Project Office",          "IT",   "EMP101", "EMP101"},
                        {"IT-DEV",   "Application Development",    "IT",   "EMP102", "EMP102,EMP114,EMP115"},
                        {"IT-CBS",   "Core Banking Support",       "IT",   "EMP103", "EMP103,EMP122"},
                        {"IT-INFRA", "Infrastructure & Database",  "IT",   "EMP116", "EMP116,EMP117"},
                        {"RB-PROD",  "Retail Products",            "RB",   "EMP105", "EMP104,EMP105"},
                        {"CB-RM",    "Relationship Management",    "CB",   "EMP106", "EMP106"},
                        {"RC-COMP",  "Compliance",                 "RC",   "EMP107", "EMP107"},
                        {"RC-AML",   "AML Desk",                   "RC",   "EMP108", "EMP108"},
                        {"OPS-CTL",  "Operations Control",         "OPS",  "EMP109", "EMP109,EMP118,EMP123"},
                        {"OPS-PAY",  "Remittance & Payments",      "OPS",  "EMP119", "EMP110,EMP119"},
                        {"OPS-VEN",  "Vendor Management",          "OPS",  "EMP121", "EMP121"},
                        {"ISEC-SOC", "Security Operations",        "ISEC", "EMP111", "EMP111"},
                        {"ISEC-DR",  "DR & Continuity",            "ISEC", "EMP120", "EMP120"},
                        {"IA-AUD",   "Audit Execution",            "IA",   "EMP112", "EMP112"},
                        {"HR-ADM",   "HR Administration",          "HR",   "EMP113", "EMP113"}}) {
                    Unit unit = new Unit();
                    unit.setCode(x[0]);
                    unit.setName(x[1]);
                    unit.setDepartment(d.get(x[2]));
                    unit.setUnitHead(e.get(x[3]));
                    u.put(x[0], unitRepo.save(unit));
                    for (String code : x[4].split(",")) {
                        Employee member = "EMP115".equals(code) ? left : e.get(code);
                        member.setUnit(u.get(x[0]));
                        employees.save(member);
                    }
                }

                // ---- employee skills (0–4 scale per CBS-GOV-003)
                // Non-critical
                skill(employeeSkills, e.get("EMP102"), s.get("Java"), 4, "10 yrs, led payment gateway build", today);
                skill(employeeSkills, e.get("EMP102"), s.get("Spring Boot"), 4, "Certified", today);
                skill(employeeSkills, e.get("EMP102"), s.get("SQL"), 3, null, today);
                skill(employeeSkills, e.get("EMP103"), s.get("Core Banking (T24/Finacle)"), 4, "T24 certified", today);
                skill(employeeSkills, e.get("EMP103"), s.get("SQL"), 3, null, today);
                skill(employeeSkills, e.get("EMP103"), s.get("Payments & SWIFT"), 2, null, today);
                skill(employeeSkills, e.get("EMP104"), s.get("Business Analysis"), 4, "CBAP", today);
                skill(employeeSkills, e.get("EMP104"), s.get("Project Management"), 2, null, today);
                skill(employeeSkills, e.get("EMP101"), s.get("Project Management"), 4, "PMP", today);
                skill(employeeSkills, e.get("EMP108"), s.get("AML / CFT"), 4, "CAMS", today);
                skill(employeeSkills, e.get("EMP108"), s.get("Regulatory Reporting"), 3, null, today);
                skill(employeeSkills, e.get("EMP107"), s.get("Regulatory Reporting"), 4, null, today);
                skill(employeeSkills, e.get("EMP107"), s.get("Credit Risk Analysis"), 3, null, today);
                skill(employeeSkills, e.get("EMP106"), s.get("Credit Risk Analysis"), 3, null, today);
                skill(employeeSkills, e.get("EMP110"), s.get("Payments & SWIFT"), 3, null, today);
                skill(employeeSkills, e.get("EMP109"), s.get("Payments & SWIFT"), 4, null, today);
                skill(employeeSkills, e.get("EMP111"), s.get("Cyber Security"), 4, "CISSP", today);
                skill(employeeSkills, e.get("EMP112"), s.get("Internal Audit"), 4, "CIA", today);
                skill(employeeSkills, e.get("EMP114"), s.get("Data Analytics"), 3, null, today);
                skill(employeeSkills, e.get("EMP114"), s.get("SQL"), 4, null, today);

                // Critical CBS skills
                skill(employeeSkills, e.get("EMP101"), s.get("CBS Application Configuration"), 4, null, today);
                skill(employeeSkills, e.get("EMP101"), s.get("Vendor Coordination"), 4, "Vendor escalation owner", today);
                skill(employeeSkills, e.get("EMP103"), s.get("CBS Release and Deployment"), 4, null, today);
                skill(employeeSkills, e.get("EMP102"), s.get("CBS Release and Deployment"), 3, null, today);
                skill(employeeSkills, e.get("EMP102"), s.get("Interface and Integration (payments, ATM, SWIFT)"), 4, "SWIFT MT/MX", today);
                skill(employeeSkills, e.get("EMP116"), s.get("Application Server / Middleware"), 4, "WebLogic + Tomcat", today);
                skill(employeeSkills, e.get("EMP116"), s.get("Java"), 3, null, today);
                skill(employeeSkills, e.get("EMP117"), s.get("Database Basics (queries, reconciliation)"), 4, "Oracle + Postgres", today);
                skill(employeeSkills, e.get("EMP117"), s.get("SQL"), 4, null, today);
                skill(employeeSkills, e.get("EMP118"), s.get("Change Management"), 4, "ITIL Foundation", today);
                skill(employeeSkills, e.get("EMP119"), s.get("Interface and Integration (payments, ATM, SWIFT)"), 3, null, today);
                skill(employeeSkills, e.get("EMP119"), s.get("Payments & SWIFT"), 4, null, today);
                skill(employeeSkills, e.get("EMP120"), s.get("DR Failover and Recovery"), 3, "DR drill lead 2024", today);
                skill(employeeSkills, e.get("EMP121"), s.get("Vendor Coordination"), 4, null, today);
                skill(employeeSkills, e.get("EMP122"), s.get("CBS Release and Deployment"), 3, null, today);
                skill(employeeSkills, e.get("EMP123"), s.get("Incident Triage and RCA"), 4, null, today);
                skill(employeeSkills, e.get("EMP107"), s.get("Vendor Coordination"), 2, null, today);

                // ---- projects
                Project p1 = project(projects, "PRJ-001", "Core Banking Upgrade", "Upgrade core banking to latest release with DR site.",
                        "ACTIVE", "CRITICAL", today.minusMonths(4), today.plusMonths(8), d.get("IT"), e.get("EMP101"));
                Project p2 = project(projects, "PRJ-002", "Mobile Banking App v2", "New customer mobile app with biometric login.",
                        "ACTIVE", "HIGH", today.minusMonths(2), today.plusMonths(6), d.get("RB"), e.get("EMP105"));
                Project p3 = project(projects, "PRJ-003", "AML Transaction Monitoring", "Replace rule engine for suspicious transaction detection.",
                        "PLANNING", "HIGH", today.plusDays(15), today.plusMonths(10), d.get("RC"), e.get("EMP107"));
                Project p4 = project(projects, "PRJ-004", "Branch Expansion Phase 3", "Open 12 new branches and 20 ATM booths.",
                        "ON_HOLD", "MEDIUM", today.minusMonths(6), today.plusMonths(12), d.get("RB"), e.get("EMP105"));
                Project p5 = project(projects, "PRJ-005", "Regulatory Reporting Automation", "Automate monthly central bank returns.",
                        "COMPLETED", "MEDIUM", today.minusMonths(10), today.minusMonths(1), d.get("RC"), e.get("EMP107"));
                Project p6 = project(projects, "PRJ-006", "ICT Security Hardening", "Close findings from last cyber security audit.",
                        "ACTIVE", "CRITICAL", today.minusMonths(1), today.plusMonths(5), d.get("ISEC"), e.get("EMP111"));
                Project p7 = project(projects, "PRJ-007", "Card Management Upgrade", "EMV 3DS2 and contactless rollout.",
                        "ACTIVE", "HIGH", today.minusMonths(3), today.plusMonths(7), d.get("RB"), e.get("EMP105"));
                Project p8 = project(projects, "PRJ-008", "Data Warehouse Migration", "Move EDW to cloud with lineage.",
                        "PLANNING", "MEDIUM", today.plusDays(30), today.plusMonths(14), d.get("IT"), e.get("EMP101"));

                // ---- the unit that owns each project
                for (Object[] x : new Object[][]{{p1, "IT-CBS"}, {p2, "RB-PROD"}, {p3, "RC-AML"}, {p4, "RB-PROD"},
                        {p5, "RC-COMP"}, {p6, "ISEC-SOC"}, {p7, "RB-PROD"}, {p8, "IT-DEV"}}) {
                    Project owned = (Project) x[0];
                    owned.setOwningUnit(u.get((String) x[1]));
                    projects.save(owned);
                }

                // ---- resource allocations
                alloc(allocations, e.get("EMP102"), p1, today.minusMonths(3), today.plusMonths(6), 50, "Backend integration");
                alloc(allocations, e.get("EMP102"), p2, today.minusMonths(2), today.plusMonths(5), 40, "API services");
                alloc(allocations, e.get("EMP102"), p7, today.minusMonths(1), today.plusMonths(6), 30, "3DS2 backend");
                alloc(allocations, e.get("EMP103"), p1, today.minusMonths(4), today.plusMonths(8), 80, "Core banking configuration");
                alloc(allocations, e.get("EMP104"), p2, today.minusMonths(2), today.plusMonths(6), 60, "Requirements and UAT");
                alloc(allocations, e.get("EMP104"), p3, today.plusDays(15), today.plusMonths(4), 30, "Requirements");
                alloc(allocations, e.get("EMP108"), p3, today.plusDays(15), today.plusMonths(10), 70, "Scenario design");
                alloc(allocations, e.get("EMP111"), p6, today.minusMonths(1), today.plusMonths(5), 90, "Security remediation lead");
                alloc(allocations, e.get("EMP114"), p2, today.minusMonths(1), today.plusMonths(4), 50, "Analytics dashboards");
                alloc(allocations, e.get("EMP114"), p8, today.plusDays(30), today.plusMonths(12), 60, "Data modelling");
                alloc(allocations, e.get("EMP101"), p1, today.minusMonths(4), today.plusMonths(8), 60, "Programme management");
                alloc(allocations, e.get("EMP101"), p6, today.minusMonths(1), today.plusMonths(5), 30, "Steering");
                alloc(allocations, e.get("EMP101"), p8, today.plusDays(30), today.plusMonths(14), 20, "Sponsor");

                // ---- work items
                WorkItem w1 = work(workItems, p1, "Configure interest accrual module", "OPEN", "HIGH", today.plusWeeks(3), 40, e.get("EMP103"), e.get("EMP101"));
                WorkItem w2 = work(workItems, p1, "DR site failover test", "IN_PROGRESS", "CRITICAL", today.plusWeeks(6), 80, e.get("EMP102"), e.get("EMP101"));
                WorkItem w3 = work(workItems, p2, "Biometric login integration", "IN_PROGRESS", "HIGH", today.plusWeeks(5), 60, e.get("EMP102"), e.get("EMP105"));
                work(workItems, p2, "Mobile UAT test cases", "OPEN", "MEDIUM", today.plusWeeks(8), 30, e.get("EMP104"), e.get("EMP105"));
                work(workItems, p3, "Vendor RFP for monitoring engine", "OPEN", "HIGH", today.plusWeeks(4), 50, e.get("EMP108"), e.get("EMP107"));
                work(workItems, p6, "Patch critical server vulnerabilities", "IN_PROGRESS", "CRITICAL", today.plusWeeks(2), 70, e.get("EMP111"), e.get("EMP111"));
                work(workItems, p5, "Final report sign-off", "DONE", "LOW", today.minusMonths(1), 8, e.get("EMP107"), e.get("EMP107"));
                work(workItems, p4, "Site survey for new branches", "OPEN", "LOW", today.plusMonths(2), 20, null, e.get("EMP105"));
                work(workItems, p7, "Integrate 3DS2 ACS", "OPEN", "HIGH", today.plusWeeks(6), 60, e.get("EMP102"), e.get("EMP105"));
                work(workItems, p8, "Source-to-target mapping", "OPEN", "MEDIUM", today.plusWeeks(10), 90, e.get("EMP114"), e.get("EMP101"));

                // ---- RACI
                raci(raci, p1, "Core banking go-live", e.get("EMP103"), RaciEntry.RaciRole.RESPONSIBLE);
                raci(raci, p1, "Core banking go-live", e.get("EMP101"), RaciEntry.RaciRole.ACCOUNTABLE);
                raci(raci, p1, "Core banking go-live", e.get("EMP111"), RaciEntry.RaciRole.CONSULTED);
                raci(raci, p1, "Core banking go-live", e.get("EMP107"), RaciEntry.RaciRole.INFORMED);
                raci(raci, p3, "AML scenario approval", e.get("EMP108"), RaciEntry.RaciRole.RESPONSIBLE);
                raci(raci, p3, "AML scenario approval", e.get("EMP107"), RaciEntry.RaciRole.ACCOUNTABLE);
                raci(raci, p3, "AML scenario approval", e.get("EMP112"), RaciEntry.RaciRole.CONSULTED);
                raci(raci, p6, "Penetration test remediation", e.get("EMP111"), RaciEntry.RaciRole.ACCOUNTABLE);
                raci(raci, p6, "Penetration test remediation", e.get("EMP102"), RaciEntry.RaciRole.RESPONSIBLE);
                raci(raci, p2, "App store release", e.get("EMP105"), RaciEntry.RaciRole.ACCOUNTABLE);
                raci(raci, p2, "App store release", e.get("EMP102"), RaciEntry.RaciRole.RESPONSIBLE);

                // ---- approvals
                approval(approvals, p3, null, e.get("EMP107"), e.get("EMP101"), "PROJECT_START", "PENDING", null);
                approval(approvals, p1, w2, e.get("EMP101"), e.get("EMP107"), "CHANGE_REQUEST", "PENDING", null);
                approval(approvals, p4, null, e.get("EMP105"), e.get("EMP101"), "PROJECT_HOLD", "APPROVED", "Budget freeze confirmed.");
                approval(approvals, p2, w3, e.get("EMP105"), e.get("EMP111"), "SECURITY_REVIEW", "REJECTED", "Biometric vendor lacks required certification.");
                approval(approvals, p5, null, e.get("EMP107"), e.get("EMP101"), "PROJECT_CLOSURE", "APPROVED", "All deliverables accepted.");

                // ---- notifications
                notify(notifications, e.get("EMP101"), "2 approval requests are waiting for your decision.", "APPROVAL", false);
                notify(notifications, e.get("EMP103"), "You were assigned: Configure interest accrual module.", "ASSIGNMENT", false);
                notify(notifications, e.get("EMP102"), "Your allocation on Mobile Banking App v2 ends in 5 months.", "ALLOCATION", true);
                notify(notifications, e.get("EMP111"), "Work item due soon: Patch critical server vulnerabilities.", "DEADLINE", false);
                notify(notifications, e.get("EMP105"), "Security review for PRJ-002 was rejected.", "APPROVAL", false);

                // ---- audit log
                audit.record("pmo.manager", "CREATE", "Project", p1.getId(), "Created project PRJ-001", "10.0.0.15");
                audit.record("pmo.manager", "CREATE", "ResourceAllocation", null, "Allocated EMP102 to PRJ-001 at 50%", "10.0.0.15");
                audit.record("dept.head", "APPROVE", "Approval", null, "Approved PROJECT_CLOSURE for PRJ-005", "10.0.0.22");
                audit.record("dept.head", "REJECT", "Approval", null, "Rejected SECURITY_REVIEW for PRJ-002", "10.0.0.22");
                audit.record("admin", "STATUS_CHANGE", "Project", p4.getId(), "PRJ-004 status ACTIVE -> ON_HOLD", "10.0.0.5");
                audit.record("auditor", "LOGIN", "AppUser", null, "Successful login", "10.0.0.31");
            }

            // ---------------------------------------------------------- 1c) link demo logins to employees (demo only, idempotent)
            if (demo) {
                link(users, "dept.head", "EMP101");
                link(users, "employee1", "EMP102");
                link(users, "pmo.manager", "EMP109");
                link(users, "auditor", "EMP112");
                link(users, "admin", "EMP001");
            }

            // ---------------------------------------------------------- 1d) first-run administrator (never with demo passwords)
            if (!demo && users.count() == 0) {
                String pw = configuredPassword == null ? "" : configuredPassword.trim();
                boolean generated = pw.isEmpty();
                if (generated) {
                    pw = randomPassword();
                } else if (pw.length() < 12 || pw.chars().noneMatch(Character::isUpperCase)
                        || pw.chars().noneMatch(Character::isLowerCase) || pw.chars().noneMatch(Character::isDigit)) {
                    throw new IllegalStateException(
                            "ERM_ADMIN_PASSWORD must be at least 12 characters with upper-case, lower-case and a digit.");
                }
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode(pw));
                admin.setRole("ROLE_ADMIN");
                admin.setEmployeeCode("EMP001");
                users.save(admin);
                if (generated) {
                    log.warn("=== First-run administrator created. Login: admin   Password: {}   (shown once - change it under My account) ===", pw);
                } else {
                    log.info("First-run administrator 'admin' created with the password from ERM_ADMIN_PASSWORD.");
                }
            }

            // ---------------------------------------------------------- 2) CBS-GOV-003 governance seed (demo only)
            if (demo && rosters.count() == 0) {
                Employee a = employees.findByEmployeeCode("EMP101").orElseThrow(); // Rahim  — Name A
                Employee b = employees.findByEmployeeCode("EMP102").orElseThrow(); // Nusrat — Name B
                Employee c = employees.findByEmployeeCode("EMP114").orElseThrow(); // Lamia  — Name C
                Employee d = employees.findByEmployeeCode("EMP120").orElseThrow(); // Rashed — DR
                Employee v = employees.findByEmployeeCode("EMP121").orElseThrow(); // Tahmina — Vendor

                // ---- backup roster
                BackupRoster prodDeploy = roster(rosters, skill(skills, "CBS Release and Deployment"), a, b, c);
                BackupRoster batch      = roster(rosters, skill(skills, "EOD / Batch Processing"),      b, c, a);
                BackupRoster iface      = roster(rosters, skill(skills, "Interface and Integration (payments, ATM, SWIFT)"), b, c, a);
                BackupRoster dr         = roster(rosters, skill(skills, "DR Failover and Recovery"),    a, d, b);
                BackupRoster vendor     = roster(rosters, skill(skills, "Vendor Coordination"),         a, v, b);

                // ---- readiness tests (performed by a backup, per CBS-GOV-003)
                readiness(tests, rosters, prodDeploy, b, LocalDate.now().minusMonths(1), BackupReadinessTest.Result.PARTIAL);
                readiness(tests, rosters, batch,      c, LocalDate.now().minusMonths(2), BackupReadinessTest.Result.PASS);
                readiness(tests, rosters, iface,      c, LocalDate.now().minusMonths(2), BackupReadinessTest.Result.PARTIAL);
                readiness(tests, rosters, dr,         d, LocalDate.now().minusMonths(3), BackupReadinessTest.Result.PARTIAL);
                readiness(tests, rosters, vendor,     v, LocalDate.now().minusMonths(1), BackupReadinessTest.Result.PARTIAL);

                // ---- cross-training plan
                plan(plans, skill(skills, "CBS Release and Deployment"),
                        "Shadow and reverse-shadow two releases for " + b.getFullName(),
                        a, LocalDate.now().plusMonths(2), CrossTrainingPlan.PlanStatus.PLANNED);
                plan(plans, skill(skills, "Interface and Integration (payments, ATM, SWIFT)"),
                        "Document the interface runbook and train " + c.getFullName(),
                        b, LocalDate.now().plusMonths(2), CrossTrainingPlan.PlanStatus.IN_PROGRESS);
                plan(plans, skill(skills, "DR Failover and Recovery"),
                        "Include " + d.getFullName() + " in the next DR drill as lead",
                        a, LocalDate.now().plusMonths(3), CrossTrainingPlan.PlanStatus.PLANNED);
                plan(plans, skill(skills, "Vendor Coordination"),
                        "Add " + b.getFullName() + " to the vendor escalation matrix",
                        a, LocalDate.now().plusMonths(2), CrossTrainingPlan.PlanStatus.PLANNED);

                // ---- six-month review
                ReviewSchedule r = new ReviewSchedule();
                r.setScope("CBS-GOV-003");
                r.setOwner(a);
                r.setDueOn(LocalDate.now().plusMonths(6));
                reviews.save(r);

                // ---- evidence
                Evidence runbook = new Evidence();
                runbook.setTitle("CBS release runbook v1.0");
                runbook.setType(Evidence.EvidenceType.RUNBOOK);
                runbook.setRecordedOn(LocalDate.now().minusMonths(1));
                runbook.setRecordedBy("pmo.manager");
                runbook.setVerificationStatus(VerificationStatus.PENDING);
                evidence.save(runbook);

                Evidence drResult = new Evidence();
                drResult.setTitle("DR readiness test result — " + LocalDate.now().minusMonths(3));
                drResult.setType(Evidence.EvidenceType.READINESS_TEST_RESULT);
                drResult.setRecordedOn(LocalDate.now().minusMonths(3));
                drResult.setRecordedBy("pmo.manager");
                drResult.setVerificationStatus(VerificationStatus.VERIFIED);
                drResult.setVerifiedOn(LocalDate.now().minusMonths(2));
                drResult = evidence.save(drResult);
                BackupReadinessTest drTest = dr.getLastTest();
                if (drTest != null) {            // evidence <-> test relation, as the app records it
                    drTest.setEvidence(drResult);
                    tests.save(drTest);
                }

                audit.record("system", "SEED", "CBS-GOV-003", null,
                        "Seeded roster, tests, plans, review and evidence", "127.0.0.1");
            }

            log.info("ERM data initializer finished (demo profile: {}).", demo);
        };
    }

    // =====================================================================
    // helpers — base + demo
    // =====================================================================

    private static String randomPassword() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString() + "9a";
    }

    private static void link(AppUserRepository users, String username, String employeeCode) {
        users.findByUsername(username).ifPresent(u -> {
            u.setEmployeeCode(employeeCode);
            users.save(u);
        });
    }

    private static void user(AppUserRepository users, PasswordEncoder enc, String name, String role) {
        if (users.findByUsername(name).isPresent()) return;
        AppUser u = new AppUser();
        u.setUsername(name);
        u.setPassword(enc.encode(DEMO_PASSWORD));
        u.setRole(role);
        users.save(u);
    }

    private static void skill(EmployeeSkillRepository repo, Employee emp, Skill sk, int level, String evidence, LocalDate on) {
        if (emp == null || sk == null) return;
        EmployeeSkill es = repo.findByEmployeeIdAndSkillId(emp.getId(), sk.getId()).orElseGet(EmployeeSkill::new);
        es.setEmployee(emp);
        es.setSkill(sk);
        es.setProficiency(level);
        es.setEvidence(evidence);
        es.setAssessedOn(on);
        repo.save(es);
    }

    private static Project project(ProjectRepository repo, String code, String name, String desc, String status, String priority,
                                   LocalDate start, LocalDate end, Department dept, Employee pm) {
        Project p = new Project();
        p.setProjectCode(code);
        p.setName(name);
        p.setDescription(desc);
        p.setStatus(status);
        p.setPriority(priority);
        p.setStartDate(start);
        p.setEndDate(end);
        p.setOwningDepartment(dept);
        p.setProjectManager(pm);
        return repo.save(p);
    }

    private static void alloc(ResourceAllocationRepository repo, Employee emp, Project p, LocalDate start, LocalDate end,
                              int percent, String notes) {
        ResourceAllocation a = new ResourceAllocation();
        a.setEmployee(emp);
        a.setProject(p);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setAllocationPercent(BigDecimal.valueOf(percent));
        a.setNotes(notes);
        repo.save(a);
    }

    private static WorkItem work(WorkItemRepository repo, Project p, String title, String status, String priority,
                                 LocalDate due, int hours, Employee assignee, Employee createdBy) {
        WorkItem w = new WorkItem();
        w.setProject(p);
        w.setTitle(title);
        w.setStatus(status);
        w.setPriority(priority);
        w.setDueDate(due);
        w.setEstimatedHours(hours);
        w.setAssignee(assignee);
        w.setCreatedBy(createdBy);
        return repo.save(w);
    }

    private static void raci(RaciEntryRepository repo, Project p, String activity, Employee emp, RaciEntry.RaciRole role) {
        RaciEntry r = new RaciEntry();
        r.setProject(p);
        r.setActivity(activity);
        r.setEmployee(emp);
        r.setRole(role);
        repo.save(r);
    }

    private static void approval(ApprovalRepository repo, Project p, WorkItem w, Employee requester, Employee approver,
                                 String type, String status, String comments) {
        Approval a = new Approval();
        a.setProject(p);
        a.setWorkItem(w);
        a.setRequestedBy(requester);
        a.setApprover(approver);
        a.setApprovalType(type);
        a.setStatus(status);
        a.setComments(comments);
        repo.save(a);
    }

    private static void notify(NotificationRepository repo, Employee to, String msg, String category, boolean read) {
        Notification n = new Notification();
        n.setRecipient(to);
        n.setMessage(msg);
        n.setCategory(category);
        n.setReadFlag(read);
        repo.save(n);
    }

    // =====================================================================
    // helpers — CBS governance
    // =====================================================================

    private static Skill skill(SkillRepository repo, String name) {
        return repo.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Missing skill: " + name));
    }

    private static BackupRoster roster(BackupRosterRepository repo, Skill activity,
                                       Employee primary, Employee b1, Employee b2) {
        BackupRoster r = new BackupRoster();
        r.setActivity(activity);
        r.setPrimaryPerson(primary);
        r.setBackup1(b1);
        r.setBackup2(b2);
        return repo.save(r);
    }

    private static void readiness(BackupReadinessTestRepository repo, BackupRosterRepository rosterRepo,
                                  BackupRoster roster, Employee performedBy, LocalDate when,
                                  BackupReadinessTest.Result result) {
        BackupReadinessTest t = new BackupReadinessTest();
        t.setRoster(roster);
        t.setPerformedBy(performedBy);
        t.setTestedOn(when);
        t.setResult(result);
        BackupReadinessTest saved = repo.save(t);
        roster.setLastTest(saved);
        rosterRepo.save(roster);
    }

    private static void plan(CrossTrainingPlanRepository repo, Skill dependency, String action,
                             Employee owner, LocalDate target, CrossTrainingPlan.PlanStatus status) {
        CrossTrainingPlan p = new CrossTrainingPlan();
        p.setDependency(dependency);
        p.setAction(action);
        p.setOwner(owner);
        p.setTargetDate(target);
        p.setStatus(status);
        repo.save(p);
    }
}