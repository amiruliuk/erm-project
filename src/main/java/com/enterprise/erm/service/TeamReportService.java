package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Team-wise reports. "Team" = the employee's UNIT (a unit sits inside a department); people who have a department
 * but no unit form a "(no unit)" team of that department. Every report can be limited to one department (teamId)
 * and / or one unit (unitId), or cover all teams. Results use {@link ReportService.Report}, so the existing report page,
 * CSV export and print view work for them unchanged.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // lazy associations (employee.department ...) are read here; open-in-view is off
public class TeamReportService {

    public static final List<String> TYPES = List.of("team-summary", "team-raci", "team-roster", "team-plans");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String NO_TEAM = "NONE";

    private final DepartmentRepository departments;
    private final UnitRepository units;
    private final EmployeeRepository employees;
    private final RaciEntryRepository raci;
    private final BackupRosterRepository rosters;
    private final CrossTrainingPlanRepository plans;
    private final ResourceAllocationRepository allocations;
    private final WorkItemRepository workItems;
    private final EmployeeCredentialRepository credentials;
    private final EmployeeSkillRepository employeeSkills;
    private final SkillRepository skills;

    public static boolean isTeamReport(String type) { return TYPES.contains(type); }

    public List<Department> teams() {
        return departments.findAll().stream().sorted(Comparator.comparing(Department::getName)).toList();
    }

    public List<Unit> unitList() {
        return units.findAll().stream()
                .sorted(Comparator.comparing((Unit u) -> u.getDepartment().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Unit::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** @return the report, or null for an unknown type */
    public ReportService.Report build(String type, Long teamId, Long unitId) {
        return switch (type) {
            case "team-summary" -> summary(teamId, unitId);
            case "team-raci" -> raciReport(teamId, unitId);
            case "team-roster" -> rosterReport(teamId, unitId);
            case "team-plans" -> planReport(teamId, unitId);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ team summary

    private ReportService.Report summary(Long teamId, Long unitId) {
        LocalDate today = LocalDate.now();
        List<Employee> all = employees.findAll();

        // teams to show: every unit (of the chosen department / the chosen unit), a "(no unit)" team for people who
        // have a department but no unit, and "Unassigned" for people with no department at all
        LinkedHashMap<String, String> teams = new LinkedHashMap<>();
        for (Department d : teams()) {
            if (teamId != null && !teamId.equals(d.getId())) continue;
            for (Unit u : units.findByDepartmentIdOrderByName(d.getId())) {
                if (unitId != null && !unitId.equals(u.getId())) continue;
                teams.put("U" + u.getId(), d.getName() + " / " + u.getName());
            }
            boolean hasUnitless = all.stream().anyMatch(e -> e.getUnit() == null
                    && e.getDepartment() != null && d.getId().equals(e.getDepartment().getId()));
            if (unitId == null && hasUnitless) teams.put("D" + d.getId(), d.getName() + " (no unit)");
        }
        if (teamId == null && unitId == null && all.stream().anyMatch(e -> e.getDepartment() == null))
            teams.put(NO_TEAM, "Unassigned");

        List<Skill> critical = skills.findByCriticalTrue();

        List<List<String>> rows = new ArrayList<>();
        for (var t : teams.entrySet()) {
            String key = t.getKey();
            List<Employee> members = all.stream()
                    .filter(e -> key.equals(key(e)) && "ACTIVE".equals(e.getEmploymentStatus())).toList();
            Set<Long> ids = new HashSet<>();
            members.forEach(m -> ids.add(m.getId()));

            // current allocation (average % per active member)
            BigDecimal allocated = BigDecimal.ZERO;
            for (ResourceAllocation a : allocations.findAll()) {
                if (!ids.contains(a.getEmployee().getId()) || a.getAllocationPercent() == null) continue;
                boolean current = (a.getStartDate() == null || !a.getStartDate().isAfter(today))
                        && (a.getEndDate() == null || !a.getEndDate().isBefore(today));
                if (current) allocated = allocated.add(a.getAllocationPercent());
            }
            String avgAlloc = members.isEmpty() ? "" : allocated
                    .divide(BigDecimal.valueOf(members.size()), 1, RoundingMode.HALF_UP).toPlainString() + "%";

            long open = 0, overdue = 0;
            for (WorkItem w : workItems.findAll()) {
                if (w.getAssignee() == null || !ids.contains(w.getAssignee().getId())) continue;
                if (isClosed(w.getStatus())) continue;
                open++;
                if (w.getDueDate() != null && w.getDueDate().isBefore(today)) overdue++;
            }

            long accountable = 0, responsible = 0;
            for (RaciEntry r : raci.findAll()) {
                if (!key.equals(key(r.getEmployee()))) continue;
                if (r.getRole() == RaciEntry.RaciRole.ACCOUNTABLE) accountable++;
                if (r.getRole() == RaciEntry.RaciRole.RESPONSIBLE) responsible++;
            }

            long primary = 0, backup = 0;
            for (BackupRoster b : rosters.findAll()) {
                if (b.getPrimaryPerson() != null && key.equals(key(b.getPrimaryPerson()))) primary++;
                if (b.getBackup1() != null && key.equals(key(b.getBackup1()))) backup++;
                if (b.getBackup2() != null && key.equals(key(b.getBackup2()))) backup++;
            }

            long openPlans = 0, latePlans = 0;
            for (CrossTrainingPlan p : plans.findAll()) {
                if (!key.equals(key(p.getOwner())) || p.getStatus() == CrossTrainingPlan.PlanStatus.DONE) continue;
                openPlans++;
                if (p.getStatus() == CrossTrainingPlan.PlanStatus.OVERDUE
                        || (p.getTargetDate() != null && p.getTargetDate().isBefore(today))) latePlans++;
            }

            long credAttention = 0, credPending = 0;
            for (EmployeeCredential c : credentials.findAll()) {
                if (!key.equals(key(c.getEmployee()))) continue;
                if (c.isExpired() || c.isExpiringSoon()) credAttention++;
                if (c.getVerificationStatus() == VerificationStatus.PENDING) credPending++;
            }

            // critical skills where at least one member of the team is at level 3+
            Set<Long> covered = new HashSet<>();
            Set<Long> criticalIds = new HashSet<>();
            critical.forEach(k -> criticalIds.add(k.getId()));
            for (EmployeeSkill es : employeeSkills.findAll()) {
                if (ids.contains(es.getEmployee().getId()) && es.getProficiency() >= 3
                        && criticalIds.contains(es.getSkill().getId())) covered.add(es.getSkill().getId());
            }

            rows.add(row(t.getValue(), String.valueOf(members.size()), avgAlloc,
                    String.valueOf(open), String.valueOf(overdue),
                    String.valueOf(accountable), String.valueOf(responsible),
                    String.valueOf(primary), String.valueOf(backup),
                    covered.size() + " / " + critical.size(),
                    String.valueOf(openPlans), String.valueOf(latePlans),
                    String.valueOf(credAttention), String.valueOf(credPending)));
        }
        return new ReportService.Report("team-summary", "Team summary",
                "One line per team (unit): people, workload, RACI, backup roster, cross-training and credential health. "
                        + "Counts people with ACTIVE status; allocation is the average current allocation per active member.",
                List.of("Team", "Active members", "Avg allocation", "Open tasks", "Overdue tasks",
                        "RACI: Accountable", "RACI: Responsible", "Roster: primary", "Roster: backup",
                        "Critical skills covered (L3+)", "Open training plans", "Late training plans",
                        "Credentials expired / expiring", "Credentials pending verification"),
                rows);
    }

    // ------------------------------------------------------------------ team RACI

    private ReportService.Report raciReport(Long teamId, Long unitId) {
        List<List<String>> rows = raci.findAll().stream()
                .filter(r -> inTeam(r.getEmployee(), teamId, unitId))
                .sorted(Comparator
                        .comparing((RaciEntry r) -> teamName(r.getEmployee()))
                        .thenComparing(r -> r.getProject().getProjectCode())
                        .thenComparing(RaciEntry::getActivity, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(r -> raciRank(r.getRole()))
                        .thenComparing(r -> r.getEmployee().getFullName()))
                .map(r -> row(teamName(r.getEmployee()),
                        nz(r.getProject().getProjectCode()) + " - " + nz(r.getProject().getName()),
                        r.getProject().getOwningUnit() != null
                                ? nz(r.getProject().getOwningUnit().getName())
                                : r.getProject().getOwningDepartment() == null ? "" : nz(r.getProject().getOwningDepartment().getName()),
                        nz(r.getActivity()),
                        r.getRole().name(),
                        nz(r.getEmployee().getEmployeeCode()),
                        nz(r.getEmployee().getFullName()),
                        nz(r.getEmployee().getJobTitle())))
                .toList();
        return new ReportService.Report("team-raci", "Team-wise RACI",
                "RACI entries grouped by the team of the person who holds the role. "
                        + "\"Project owner\" is the unit (or department) that owns the project, so cross-team work is visible.",
                List.of("Team", "Project", "Project owner", "Activity", "Role", "Emp code", "Person", "Job title"),
                rows);
    }

    // ------------------------------------------------------------------ team roster

    private ReportService.Report rosterReport(Long teamId, Long unitId) {
        List<List<String>> rows = new ArrayList<>();
        List<BackupRoster> all = new ArrayList<>(rosters.findAll());
        all.sort(Comparator.comparing(b -> b.getActivity().getName(), String.CASE_INSENSITIVE_ORDER));
        for (BackupRoster b : all) {
            String test = b.getLastTest() == null ? "Never tested"
                    : date(b.getLastTest().getTestedOn()) + " " + b.getLastTest().getResult();
            String primaryName = b.getPrimaryPerson() == null ? "" : nz(b.getPrimaryPerson().getFullName());
            addRosterRow(rows, teamId, unitId, b, "Primary", b.getPrimaryPerson(), primaryName, test);
            addRosterRow(rows, teamId, unitId, b, "Backup 1", b.getBackup1(), primaryName, test);
            addRosterRow(rows, teamId, unitId, b, "Backup 2", b.getBackup2(), primaryName, test);
        }
        rows.sort(Comparator.<List<String>, String>comparing(r -> r.get(0))
                .thenComparing(r -> r.get(1), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.get(2)));
        return new ReportService.Report("team-roster", "Team-wise backup roster",
                "Who covers which critical activity, grouped by the team of the person. "
                        + "Last test is the latest readiness test of the activity.",
                List.of("Team", "Critical activity", "Role", "Emp code", "Person", "Primary person", "Last readiness test"),
                rows);
    }

    private void addRosterRow(List<List<String>> rows, Long teamId, Long unitId, BackupRoster b, String role,
                              Employee person, String primaryName, String test) {
        if (person == null || !inTeam(person, teamId, unitId)) return;
        rows.add(row(teamName(person), nz(b.getActivity().getName()), role,
                nz(person.getEmployeeCode()), nz(person.getFullName()), primaryName, test));
    }

    // ------------------------------------------------------------------ team cross-training plans

    private ReportService.Report planReport(Long teamId, Long unitId) {
        LocalDate today = LocalDate.now();
        List<List<String>> rows = plans.findAll().stream()
                .filter(p -> inTeam(p.getOwner(), teamId, unitId))
                .sorted(Comparator
                        .comparing((CrossTrainingPlan p) -> teamName(p.getOwner()))
                        .thenComparing(p -> p.getTargetDate() == null ? LocalDate.MAX : p.getTargetDate()))
                .map(p -> row(teamName(p.getOwner()),
                        nz(p.getDependency().getName()),
                        nz(p.getAction()),
                        nz(p.getOwner().getFullName()),
                        date(p.getTargetDate()),
                        p.getStatus().name(),
                        p.getStatus() == CrossTrainingPlan.PlanStatus.DONE || p.getTargetDate() == null ? ""
                                : String.valueOf(ChronoUnit.DAYS.between(today, p.getTargetDate()))))
                .toList();
        return new ReportService.Report("team-plans", "Team-wise cross-training plans",
                "Mitigation / cross-training actions by the team of the plan owner. Negative days = past the target date.",
                List.of("Team", "Critical skill", "Action", "Owner", "Target date", "Status", "Days to target"),
                rows);
    }

    // ------------------------------------------------------------------ helpers

    /** U<unitId> when the person has a unit, else D<departmentId>, else NONE. */
    private static String key(Employee e) {
        if (e == null) return NO_TEAM;
        if (e.getUnit() != null) return "U" + e.getUnit().getId();
        return e.getDepartment() == null ? NO_TEAM : "D" + e.getDepartment().getId();
    }

    /** teamId filters by department, unitId by unit; both null = everyone. */
    private static boolean inTeam(Employee e, Long teamId, Long unitId) {
        if (unitId != null) return e != null && e.getUnit() != null && unitId.equals(e.getUnit().getId());
        if (teamId != null) return e != null && e.getDepartment() != null && teamId.equals(e.getDepartment().getId());
        return true;
    }

    private static String teamName(Employee e) {
        if (e == null || e.getDepartment() == null) return "Unassigned";
        String dept = nz(e.getDepartment().getName());
        return e.getUnit() == null ? dept + " (no unit)" : dept + " / " + nz(e.getUnit().getName());
    }

    private static int raciRank(RaciEntry.RaciRole r) {
        return switch (r) { case ACCOUNTABLE -> 0; case RESPONSIBLE -> 1; case CONSULTED -> 2; case INFORMED -> 3; };
    }

    private static boolean isClosed(String status) {
        return "DONE".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status);
    }

    private static List<String> row(String... cells) { return new ArrayList<>(Arrays.asList(cells)); }

    private static String date(LocalDate d) { return d == null ? "" : d.format(DATE); }

    private static String nz(String s) { return s == null ? "" : s; }
}
