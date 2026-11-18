package com.enterprise.erm.web;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import com.enterprise.erm.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.enterprise.erm.web.WebSupport.*;

/** Project register. */
@Controller
@RequiredArgsConstructor
public class ProjectController {
    private static final List<String> PROJECT_STATUSES = List.of("PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED");

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final DepartmentRepository departments;
    private final UnitRepository units;
    private final UnitService unitService;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- projects

    @GetMapping("/projects")
    public String projects(@RequestParam(required = false) Long unitId, Model m) {
        m.addAttribute("projects", unitId == null ? projects.findAll() : projects.findByOwningUnitId(unitId));
        m.addAttribute("departments", departments.findAll());
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("projectStatuses", PROJECT_STATUSES);
        return "projects";
    }

    @PostMapping("/projects")
    public String saveProject(@RequestParam String projectCode,
                              @RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) Long departmentId,
                              @RequestParam(required = false) Long unitId,
                              @RequestParam(required = false) Long projectManagerId,
                              @RequestParam(required = false) LocalDate startDate,
                              @RequestParam(required = false) LocalDate endDate,
                              @RequestParam(defaultValue = "MEDIUM") String priority,
                              HttpServletRequest req,
                              RedirectAttributes ra) {
        if (isBlank(projectCode) || isBlank(name)) {
            return fail(ra, "/projects", "Project code and name are required.");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            return fail(ra, "/projects", "Project end date cannot be before its start date.");
        }
        var p = new Project();
        p.setProjectCode(projectCode.trim());
        p.setName(name.trim());
        p.setDescription(blankToNull(description));
        p.setStatus("PLANNING");
        p.setPriority(PRIORITIES.contains(priority) ? priority : "MEDIUM");
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        if (projectManagerId != null) employees.findById(projectManagerId).ifPresent(p::setProjectManager);
        Department ownerDept = departmentId == null ? null : departments.findById(departmentId).orElse(null);
        Unit ownerUnit = unitId == null ? null : units.findWithDetailsById(unitId).orElse(null);
        if (unitId != null && ownerUnit == null) return fail(ra, "/projects", "Selected unit no longer exists.");
        if (ownerUnit != null) {
            if (ownerDept == null) ownerDept = ownerUnit.getDepartment();       // a unit implies its department
            else if (!ownerUnit.getDepartment().getId().equals(ownerDept.getId()))
                return fail(ra, "/projects", "Unit " + ownerUnit.getName() + " does not belong to the selected department.");
        }
        p.setOwningDepartment(ownerDept);
        p.setOwningUnit(ownerUnit);
        try {
            projects.save(p);
        } catch (DataIntegrityViolationException ex) {
            return fail(ra, "/projects", "A project with that code already exists.");
        }
        logger.log(req, "CREATE", "Project", p.getId(), "Created project " + p.getProjectCode());
        return ok(ra, "/projects", "Project created.");
    }

    @PostMapping("/projects/{id}/status")
    public String updateProjectStatus(@PathVariable Long id,
                                      @RequestParam String status,
                                      HttpServletRequest req,
                                      RedirectAttributes ra) {
        var project = projects.findById(id).orElse(null);
        if (project == null) return fail(ra, "/projects", "Project not found.");
        if (!PROJECT_STATUSES.contains(status)) return fail(ra, "/projects", "Invalid project status.");
        String old = project.getStatus();
        project.setStatus(status);
        projects.save(project);
        logger.log(req, "STATUS_CHANGE", "Project", id, project.getProjectCode() + " status " + old + " -> " + status);
        return ok(ra, "/projects", "Project status updated.");
    }


}
