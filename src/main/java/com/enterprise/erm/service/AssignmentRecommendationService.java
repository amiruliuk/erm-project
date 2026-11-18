package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentRecommendationService {

    private final EmployeeRepository employees;
    private final EmployeeSkillRepository employeeSkills;
    private final ResourceAllocationRepository allocations;

    public List<Map<String, Object>> recommend(Long skillId, int requiredLevel) {
        return recommend(skillId, requiredLevel, null);
    }

    /** @param unitId when not null, only members of that unit are ranked (unit-wise assignment) */
    public List<Map<String, Object>> recommend(Long skillId, int requiredLevel, Long unitId) {
        // best proficiency per employee for the requested skill
        Map<Long, Integer> skillScores = employeeSkills.findAll().stream()
                .filter(es -> es.getSkill() != null && es.getEmployee() != null && es.getSkill().getId().equals(skillId))
                .collect(Collectors.toMap(es -> es.getEmployee().getId(), EmployeeSkill::getProficiency, Math::max));

        // total allocation per employee, loaded once (previously re-queried for every employee)
        Map<Long, Double> allocated = allocations.findAll().stream()
                .filter(a -> a.getEmployee() != null
                        && (a.getStartDate() == null || !a.getStartDate().isAfter(LocalDate.now()))
                        && (a.getEndDate() == null || !a.getEndDate().isBefore(LocalDate.now())))
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(),
                        Collectors.summingDouble(a -> a.getAllocationPercent() == null ? 0 : a.getAllocationPercent().doubleValue())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Employee e : employees.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(e.getEmploymentStatus())) continue;
            if (unitId != null && (e.getUnit() == null || !unitId.equals(e.getUnit().getId()))) continue;
            int level = skillScores.getOrDefault(e.getId(), 0);
            double capacity = Math.max(0, 100 - allocated.getOrDefault(e.getId(), 0.0));
            double fit = requiredLevel <= 0 ? 100 : Math.min(100, level * 100.0 / requiredLevel);
            long score = Math.round(fit * 0.7 + capacity * 0.3);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employee", e);
            row.put("skillLevel", level);
            row.put("availableCapacity", Math.round(capacity));
            row.put("score", score);
            result.add(row);
        }
        // the old comparator cast a Long to (double), which throws ClassCastException with 2+ employees
        result.sort((a, b) -> Long.compare((Long) b.get("score"), (Long) a.get("score")));
        return result;
    }
}
