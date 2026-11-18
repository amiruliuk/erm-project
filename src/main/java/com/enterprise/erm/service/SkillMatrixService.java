package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.domain.dto.SkillMatrixCell;
import com.enterprise.erm.domain.dto.SkillMatrixGroup;
import com.enterprise.erm.domain.dto.SkillMatrixReport;
import com.enterprise.erm.domain.dto.SkillMatrixRow;
import com.enterprise.erm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillMatrixService {

    private final SkillRepository skills;
    private final EmployeeSkillRepository employeeSkills;
    private final EmployeeRepository employees;

    public SkillMatrixReport buildMatrix() {
        // Active employees, grouped by unit (department / unit), ordered by group label then employee code.
        List<Employee> active = employees.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getEmploymentStatus()))
                .sorted(Comparator
                        .comparing((Employee e) -> e.getDepartment() == null ? "~" : groupLabel(e))
                        .thenComparing(Employee::getEmployeeCode))
                .toList();

        // Ordered list of groups (stable: first occurrence wins). Key: unit id, else department id, else none.
        LinkedHashMap<String, SkillMatrixGroup> groupMap = new LinkedHashMap<>();
        for (Employee e : active) {
            Department d = e.getDepartment();
            String key = e.getUnit() != null ? "U" + e.getUnit().getId() : d != null ? "D" + d.getId() : "NONE";
            String name = d == null ? "Unassigned" : groupLabel(e);
            groupMap.computeIfAbsent(key, k -> new SkillMatrixGroup(
                    d == null ? null : d.getId(),
                    name,
                    new ArrayList<>(),
                    new ArrayList<>()));
            groupMap.get(key).employeeCodes().add(e.getEmployeeCode());
        }
        // Build the "empty cells" template per group once; rows will supply levels.
        List<SkillMatrixGroup> groups = new ArrayList<>(groupMap.values());

        // Index EmployeeSkill by (skillId, employeeId).
        Map<Long, Map<Long, EmployeeSkill>> bySkill = new HashMap<>();
        for (EmployeeSkill es : employeeSkills.findAll()) {
            bySkill.computeIfAbsent(es.getSkill().getId(), k -> new HashMap<>())
                    .put(es.getEmployee().getId(), es);
        }

        // Map employeeCode -> employeeId for quick lookup in toRow.
        Map<String, Long> codeToId = active.stream()
                .collect(Collectors.toMap(Employee::getEmployeeCode, Employee::getId));

        List<SkillMatrixRow> rows = skills.findAll().stream()
                .sorted(Comparator.comparing(Skill::getName))
                .map(skill -> toRow(skill, groups, bySkill.getOrDefault(skill.getId(), Map.of()), codeToId))
                .toList();

        return new SkillMatrixReport(groups, rows);
    }

    public SkillMatrixReport buildCriticalMatrix() {
        SkillMatrixReport full = buildMatrix();
        return new SkillMatrixReport(
                full.groups(),
                full.rows().stream().filter(SkillMatrixRow::critical).toList());
    }

    private SkillMatrixRow toRow(Skill skill, List<SkillMatrixGroup> groups,
                                 Map<Long, EmployeeSkill> cellsByEmployeeId,
                                 Map<String, Long> codeToId) {
        List<SkillMatrixGroup> rowGroups = new ArrayList<>();
        long at3 = 0;

        for (SkillMatrixGroup g : groups) {
            List<SkillMatrixCell> cells = new ArrayList<>();
            for (String code : g.employeeCodes()) {
                Long empId = codeToId.get(code);
                EmployeeSkill es = empId == null ? null : cellsByEmployeeId.get(empId);
                int level = es == null ? 0 : es.getProficiency();
                if (level >= 3) at3++;
                cells.add(new SkillMatrixCell(code, null, level));  // name not needed in cells
            }
            rowGroups.add(new SkillMatrixGroup(g.departmentId(), g.departmentName(),
                    g.employeeCodes(), cells));
        }

        boolean risk = skill.isCritical() && at3 < 2;
        return new SkillMatrixRow(skill.getId(), skill.getName(), skill.isCritical(),
                at3, risk, rowGroups);
    }

    /** "Department / Unit" for people in a unit, "Department (no unit)" for people with a department only. */
    private static String groupLabel(Employee e) {
        String dept = e.getDepartment().getName();
        return e.getUnit() == null ? dept + " (no unit)" : dept + " / " + e.getUnit().getName();
    }
}
