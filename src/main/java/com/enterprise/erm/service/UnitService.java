package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Unit CRUD plus the "unit workspace": one page that shows everything that belongs to a unit. */
@Service
@RequiredArgsConstructor
@Transactional
public class UnitService {

    /** A titled table of plain strings. Built inside the transaction so the page never touches lazy data. */
    public record Section(String title, List<String> headers, List<List<String>> rows) {}

    /** Everything about one unit, flattened for the template. */
    public record Workspace(Long id, String name, String code, String description, boolean active,
                            Long departmentId, String departmentName, String headName,
                            LinkedHashMap<String, String> kpis, List<Section> sections) {}

    private final UnitRepository units;
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final WorkItemRepository workItems;
    private final ResourceAllocationRepository allocations;
    private final RaciEntryRepository raci;
    private final BackupRosterRepository rosters;
    private final CrossTrainingPlanRepository plans;
    private final EmployeeCredentialRepository credentials;
    private final EmployeeSkillRepository employeeSkills;
    private final SkillRepository skills;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<Unit> findAll() {
        return units.findAll().stream()
                .sorted(Comparator.comparing((Unit u) -> u.getDepartment().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Unit::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Unit> findActive() {
        return findAll().stream().filter(Unit::isActive).toList();
    }

    @Transactional(readOnly = true)
    public Unit findById(Long id) {
        return units.findWithDetailsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + id));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> unitCountsByDepartment() {
        Map<Long, Long> counts = new HashMap<>();
        for (Unit u : units.findAll()) counts.merge(u.getDepartment().getId(), 1L, Long::sum);
        return counts;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> memberCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Employee e : employees.findAll()) {
            if (e.getUnit() != null && "ACTIVE".equals(e.getEmploymentStatus()))
                counts.merge(e.getUnit().getId(), 1L, Long::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------ commands

    public Unit create(String name, String code, String description, Long departmentId, Long headId, boolean active) {
        Department dept = departments.findById(departmentId == null ? -1L : departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Select a department."));
        Unit u = new Unit();
        apply(u, name, code, description, dept, headId, active);
        return units.save(u);
    }

    public Unit update(Long id, String name, String code, String description, Long departmentId, Long headId, boolean active) {
        Unit u = findById(id);
        Department dept = departments.findById(departmentId == null ? -1L : departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Select a department."));
        // moving a unit to another department would leave its people / projects in the wrong department
        if (!u.getDepartment().getId().equals(dept.getId())
                && (employees.countByUnitId(id) > 0 || projects.countByOwningUnitId(id) > 0)) {
            throw new IllegalArgumentException(
                    "This unit still has employees or projects, so it cannot be moved to another department. Re-assign them first.");
        }
        apply(u, name, code, description, dept, headId, active);
        return units.save(u);
    }

    public void delete(Long id) {
        Unit u = findById(id);
        long people = employees.countByUnitId(id);
        long projs = projects.countByOwningUnitId(id);
        if (people > 0 || projs > 0) {
            throw new IllegalStateException("This unit still has " + people + " employee(s) and " + projs
                    + " project(s). Re-assign them, or mark the unit inactive instead of deleting it.");
        }
        units.delete(u);
    }

    private void apply(Unit u, String name, String code, String description, Department dept, Long headId, boolean active) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Unit name is required.");
        String n = name.trim();
        String c = (code == null || code.isBlank()) ? null : code.trim();
        Long selfId = u.getId();

        boolean nameTaken = selfId == null
                ? units.existsByDepartmentIdAndNameIgnoreCase(dept.getId(), n)
                : units.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(dept.getId(), n, selfId);
        if (nameTaken) throw new IllegalArgumentException("Unit name already exists in " + dept.getName() + ": " + n);
        if (c != null) {
            boolean codeTaken = selfId == null ? units.existsByCode(c) : units.existsByCodeAndIdNot(c, selfId);
            if (codeTaken) throw new IllegalArgumentException("Unit code already exists: " + c);
        }
        if (!dept.isActive() && (selfId == null || !dept.getId().equals(u.getDepartment().getId())))
            throw new IllegalArgumentException("Department " + dept.getName() + " is inactive.");

        Employee head = null;
        if (headId != null) {
            head = employees.findById(headId).orElseThrow(() -> new IllegalArgumentException("Unit head not found."));
            if (head.getDepartment() == null || !head.getDepartment().getId().equals(dept.getId()))
                throw new IllegalArgumentException("The unit head must belong to the " + dept.getName() + " department.");
            if (!"ACTIVE".equals(head.getEmploymentStatus()))
                throw new IllegalArgumentException("The unit head must be an ACTIVE employee.");
        }
        u.setName(n);
        u.setCode(c);
        u.setDescription(description == null || description.isBlank() ? null : description.trim());
        u.setDepartment(dept);
        u.setUnitHead(head);
        u.setActive(active);
    }

    /**
     * Applies a department + unit choice to an employee, keeping the two consistent.
     * @return an error message, or null when the assignment is valid
     */
    public String assign(Employee e, Long departmentId, Long unitId) {
        Department dept = null;
        if (departmentId != null) {
            dept = departments.findById(departmentId).orElse(null);
            if (dept == null) return "Selected department no longer exists.";
        }
        Unit unit = null;
        if (unitId != null) {
            unit = units.findWithDetailsById(unitId).orElse(null);
            if (unit == null) return "Selected unit no longer exists.";
            if (!unit.isActive()) return "Unit " + unit.getName() + " is inactive.";
            if (dept == null) dept = unit.getDepartment();                       // unit implies its department
            if (!unit.getDepartment().getId().equals(dept.getId()))
                return "Unit " + unit.getName() + " does not belong to the " + dept.getName() + " department.";
        }
        e.setDepartment(dept);
        e.setUnit(unit);
        // a unit head must stay inside the unit's department: moving the person away removes the head role
        if (e.getId() != null) {
            for (Unit headed : units.findByUnitHeadId(e.getId())) {
                if (dept == null || !headed.getDepartment().getId().equals(dept.getId())) headed.setUnitHead(null);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ workspace

    @Transactional(readOnly = true)
    public Workspace workspace(Long id) {
        Unit u = findById(id);
        LocalDate today = LocalDate.now();

        List<Employee> members = new ArrayList<>(employees.findByUnitId(id));
        members.sort(Comparator.comparing(Employee::getEmployeeCode));
        Set<Long> memberIds = new HashSet<>();
        Set<Long> activeIds = new HashSet<>();
        for (Employee m : members) {
            memberIds.add(m.getId());
            if ("ACTIVE".equals(m.getEmploymentStatus())) activeIds.add(m.getId());
        }

        // ---- allocations (current % per member)
        List<ResourceAllocation> allocs = allocations.findByEmployeeUnitId(id);
        Map<Long, BigDecimal> currentPct = new HashMap<>();
        for (ResourceAllocation a : allocs) {
            if (a.getAllocationPercent() == null) continue;
            boolean current = (a.getStartDate() == null || !a.getStartDate().isAfter(today))
                    && (a.getEndDate() == null || !a.getEndDate().isBefore(today));
            if (current) currentPct.merge(a.getEmployee().getId(), a.getAllocationPercent(), BigDecimal::add);
        }
        BigDecimal totalPct = BigDecimal.ZERO;
        for (Long eid : activeIds) totalPct = totalPct.add(currentPct.getOrDefault(eid, BigDecimal.ZERO));
        String avgAlloc = activeIds.isEmpty() ? "-"
                : totalPct.divide(BigDecimal.valueOf(activeIds.size()), 1, RoundingMode.HALF_UP).toPlainString() + "%";

        // ---- projects & tasks
        List<Project> unitProjects = new ArrayList<>(projects.findByOwningUnitId(id));
        unitProjects.sort(Comparator.comparing(Project::getProjectCode));
        long activeProjects = unitProjects.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count();

        List<WorkItem> tasks = new ArrayList<>(workItems.findForUnit(id));
        tasks.removeIf(w -> isClosed(w.getStatus()));
        tasks.sort(Comparator.comparing((WorkItem w) -> w.getDueDate() == null ? LocalDate.MAX : w.getDueDate()));
        long overdue = tasks.stream().filter(w -> w.getDueDate() != null && w.getDueDate().isBefore(today)).count();

        // ---- RACI
        List<RaciEntry> raciEntries = new ArrayList<>(raci.findByEmployeeUnitId(id));
        raciEntries.sort(Comparator.comparing((RaciEntry r) -> r.getProject().getProjectCode())
                .thenComparing(RaciEntry::getActivity, String.CASE_INSENSITIVE_ORDER));

        // ---- backup roster (rows where a unit member is primary or backup)
        List<List<String>> rosterRows = new ArrayList<>();
        long primary = 0, backup = 0;
        for (BackupRoster b : rosters.findByActivityCriticalTrue()) {
            String test = b.getLastTest() == null ? "Never tested"
                    : b.getLastTest().getTestedOn() + " " + b.getLastTest().getResult();
            if (b.getPrimaryPerson() != null && memberIds.contains(b.getPrimaryPerson().getId())) {
                primary++;
                rosterRows.add(row(b.getActivity().getName(), "Primary", b.getPrimaryPerson().getFullName(), test));
            }
            if (b.getBackup1() != null && memberIds.contains(b.getBackup1().getId())) {
                backup++;
                rosterRows.add(row(b.getActivity().getName(), "Backup 1", b.getBackup1().getFullName(), test));
            }
            if (b.getBackup2() != null && memberIds.contains(b.getBackup2().getId())) {
                backup++;
                rosterRows.add(row(b.getActivity().getName(), "Backup 2", b.getBackup2().getFullName(), test));
            }
        }

        // ---- cross-training plans owned by unit members
        List<List<String>> planRows = new ArrayList<>();
        long openPlans = 0, latePlans = 0;
        for (CrossTrainingPlan p : plans.findAll()) {
            if (!memberIds.contains(p.getOwner().getId()) || p.getStatus() == CrossTrainingPlan.PlanStatus.DONE) continue;
            openPlans++;
            boolean late = p.getStatus() == CrossTrainingPlan.PlanStatus.OVERDUE
                    || (p.getTargetDate() != null && p.getTargetDate().isBefore(today));
            if (late) latePlans++;
            planRows.add(row(p.getDependency().getName(), nz(p.getAction()), p.getOwner().getFullName(),
                    p.getTargetDate() == null ? "" : p.getTargetDate().toString(), p.getStatus().name()));
        }

        // ---- credentials needing attention
        List<List<String>> credRows = new ArrayList<>();
        long credAttention = 0, credPending = 0;
        for (EmployeeCredential c : credentials.findByEmployeeUnitId(id)) {
            boolean attention = c.isExpired() || c.isExpiringSoon();
            boolean pending = c.getVerificationStatus() == VerificationStatus.PENDING;
            if (attention) credAttention++;
            if (pending) credPending++;
            if (attention || pending) {
                credRows.add(row(c.getEmployee().getFullName(), nz(c.getTitle()),
                        c.getExpiryDate() == null ? "" : c.getExpiryDate().toString(),
                        c.isExpired() ? "EXPIRED" : c.isExpiringSoon() ? "EXPIRING SOON" : "",
                        String.valueOf(c.getVerificationStatus())));
            }
        }

        // ---- critical skills covered by the unit (someone at level 3+)
        Set<Long> criticalIds = new HashSet<>();
        skills.findByCriticalTrue().forEach(k -> criticalIds.add(k.getId()));
        Set<Long> covered = new HashSet<>();
        for (EmployeeSkill es : employeeSkills.findAll()) {
            if (activeIds.contains(es.getEmployee().getId()) && es.getProficiency() >= 3
                    && criticalIds.contains(es.getSkill().getId())) covered.add(es.getSkill().getId());
        }

        // ---- KPIs
        LinkedHashMap<String, String> kpis = new LinkedHashMap<>();
        kpis.put("Active members", String.valueOf(activeIds.size()));
        kpis.put("Avg allocation", avgAlloc);
        kpis.put("Projects (active / total)", activeProjects + " / " + unitProjects.size());
        kpis.put("Open tasks", String.valueOf(tasks.size()));
        kpis.put("Overdue tasks", String.valueOf(overdue));
        kpis.put("Roster: primary / backup", primary + " / " + backup);
        kpis.put("Critical skills covered (L3+)", covered.size() + " / " + criticalIds.size());
        kpis.put("Open / late training plans", openPlans + " / " + latePlans);
        kpis.put("Credentials: attention / pending", credAttention + " / " + credPending);

        // ---- sections
        List<Section> sections = new ArrayList<>();

        List<List<String>> memberRows = new ArrayList<>();
        for (Employee m : members) {
            memberRows.add(row(m.getEmployeeCode(), m.getFullName(), nz(m.getJobTitle()), m.getEmploymentStatus(),
                    currentPct.getOrDefault(m.getId(), BigDecimal.ZERO).toPlainString() + "%"));
        }
        sections.add(new Section("Members", List.of("Code", "Name", "Job title", "Status", "Current allocation"), memberRows));

        List<List<String>> projRows = new ArrayList<>();
        for (Project p : unitProjects) {
            projRows.add(row(p.getProjectCode(), p.getName(),
                    p.getProjectManager() == null ? "" : p.getProjectManager().getFullName(),
                    p.getStatus(), p.getPriority(),
                    (p.getStartDate() == null ? "-" : p.getStartDate()) + " to " + (p.getEndDate() == null ? "-" : p.getEndDate())));
        }
        sections.add(new Section("Projects owned by this unit",
                List.of("Code", "Project", "Manager", "Status", "Priority", "Schedule"), projRows));

        List<List<String>> taskRows = new ArrayList<>();
        for (WorkItem w : tasks) {
            boolean late = w.getDueDate() != null && w.getDueDate().isBefore(today);
            taskRows.add(row(w.getTitle(), w.getProject().getProjectCode(),
                    w.getAssignee() == null ? "Unassigned" : w.getAssignee().getFullName(),
                    w.getStatus(), w.getPriority(),
                    w.getDueDate() == null ? "" : w.getDueDate() + (late ? " (overdue)" : "")));
        }
        sections.add(new Section("Open tasks (assigned to members, or in the unit's projects)",
                List.of("Task", "Project", "Owner", "Status", "Priority", "Due"), taskRows));

        List<List<String>> allocRows = new ArrayList<>();
        for (ResourceAllocation a : allocs) {
            allocRows.add(row(a.getEmployee().getFullName(), a.getProject().getName(),
                    (a.getStartDate() == null ? "-" : a.getStartDate()) + " to " + (a.getEndDate() == null ? "-" : a.getEndDate()),
                    a.getAllocationPercent() == null ? "" : a.getAllocationPercent().toPlainString() + "%"));
        }
        sections.add(new Section("Resource allocations", List.of("Employee", "Project", "Period", "Allocation"), allocRows));

        List<List<String>> raciRows = new ArrayList<>();
        for (RaciEntry r : raciEntries) {
            raciRows.add(row(r.getProject().getProjectCode(), r.getActivity(), r.getEmployee().getFullName(), r.getRole().name()));
        }
        sections.add(new Section("RACI roles held by members", List.of("Project", "Activity", "Person", "Role"), raciRows));
        sections.add(new Section("Backup roster coverage", List.of("Critical activity", "Role", "Person", "Last readiness test"), rosterRows));
        sections.add(new Section("Cross-training plans", List.of("Critical skill", "Action", "Owner", "Target date", "Status"), planRows));
        sections.add(new Section("Credentials needing attention",
                List.of("Employee", "Credential", "Expiry", "Flag", "Verification"), credRows));

        return new Workspace(u.getId(), u.getName(), u.getCode(), u.getDescription(), u.isActive(),
                u.getDepartment().getId(), u.getDepartment().getName(),
                u.getUnitHead() == null ? null : u.getUnitHead().getFullName(), kpis, sections);
    }

    private static boolean isClosed(String s) { return "DONE".equalsIgnoreCase(s) || "CANCELLED".equalsIgnoreCase(s); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static List<String> row(String... cells) { return new ArrayList<>(Arrays.asList(cells)); }
}
