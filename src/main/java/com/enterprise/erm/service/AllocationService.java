package com.enterprise.erm.service;

import com.enterprise.erm.domain.ResourceAllocation;
import com.enterprise.erm.repository.EmployeeRepository;
import com.enterprise.erm.repository.ProjectRepository;
import com.enterprise.erm.repository.ResourceAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creates resource allocations. The employee row is locked for the duration of the transaction,
 * so two simultaneous requests cannot both pass the 100% capacity check.
 */
@Service
@RequiredArgsConstructor
public class AllocationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final ResourceAllocationRepository allocations;

    /** @return null on success, otherwise a user-facing error message */
    @Transactional
    public String allocate(Long employeeId, Long projectId, LocalDate start, LocalDate end,
                           BigDecimal percent, String notes) {
        if (employeeId == null || projectId == null) return "Select a valid employee and project.";
        if (start == null || end == null || end.isBefore(start)) return "Enter a valid allocation date range.";
        if (percent == null || percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0)
            return "Allocation must be greater than 0 and no more than 100 percent.";

        var employee = employees.lockById(employeeId).orElse(null);
        var project = projects.findById(projectId).orElse(null);
        if (employee == null || project == null) return "Select a valid employee and project.";
        if (!"ACTIVE".equalsIgnoreCase(employee.getEmploymentStatus()))
            return "Only ACTIVE employees can be allocated to projects.";

        BigDecimal committed = allocations.findByEmployeeId(employeeId).stream()
                .filter(a -> a.getStartDate() != null && a.getEndDate() != null)
                .filter(a -> !a.getEndDate().isBefore(start) && !a.getStartDate().isAfter(end))
                .map(ResourceAllocation::getAllocationPercent)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (committed.add(percent).compareTo(HUNDRED) > 0)
            return "This allocation exceeds the employee's available capacity for the selected dates. "
                    + "Existing overlapping allocation: " + committed.stripTrailingZeros().toPlainString() + "%.";

        var a = new ResourceAllocation();
        a.setEmployee(employee);
        a.setProject(project);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setAllocationPercent(percent);
        a.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        allocations.save(a);
        return null;
    }
}
