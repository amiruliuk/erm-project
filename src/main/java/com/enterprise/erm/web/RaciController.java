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

/** RACI register (employees see their own entries). */
@Controller
@RequiredArgsConstructor
public class RaciController {

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final UnitRepository units;
    private final UnitService unitService;
    private final RaciEntryRepository raci;
    private final CurrentUserService currentUser;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- RACI (employees see their own entries)

    @GetMapping("/raci")
    public String raci(@RequestParam(required = false) Long projectId,
                       @RequestParam(required = false) Long unitId, Model m) {
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        m.addAttribute("projects", projects.findAll());
        m.addAttribute("employees", employees.findAll());
        List<RaciEntry> entries;
        if (currentUser.isRestricted()) {
            entries = currentUser.employee().map(e -> raci.findByEmployeeId(e.getId())).orElse(List.of());
        } else {
            entries = unitId != null ? raci.findByEmployeeUnitId(unitId)
                    : projectId == null ? raci.findAll() : raci.findByProjectId(projectId);
        }
        m.addAttribute("entries", entries);
        return "raci";
    }

    @PostMapping("/raci")
    public String saveRaci(@RequestParam Long projectId,
                           @RequestParam Long employeeId,
                           @RequestParam String activity,
                           @RequestParam RaciEntry.RaciRole role,
                           HttpServletRequest req, RedirectAttributes ra) {
        var project = projects.findById(projectId).orElse(null);
        var employee = employees.findById(employeeId).orElse(null);
        if (project == null || employee == null || isBlank(activity)) {
            return fail(ra, "/raci", "Project, employee and activity are required.");
        }
        String act = activity.trim();
        if (raci.existsByProjectIdAndActivityIgnoreCaseAndEmployeeId(projectId, act, employeeId))
            return fail(ra, "/raci", "That person already has a RACI role on this activity. Remove it first to change it.");
        if (role == RaciEntry.RaciRole.ACCOUNTABLE
                && raci.existsByProjectIdAndActivityIgnoreCaseAndRole(projectId, act, RaciEntry.RaciRole.ACCOUNTABLE))
            return fail(ra, "/raci", "Each activity can have only one Accountable person.");
        var r = new RaciEntry();
        r.setProject(project);
        r.setEmployee(employee);
        r.setActivity(activity.trim());
        r.setRole(role);
        raci.save(r);
        logger.log(req, "CREATE", "RaciEntry", r.getId(), r.getRole() + ": " + employee.getEmployeeCode() + " on '" + r.getActivity() + "' (" + project.getProjectCode() + ")");
        return ok(ra, "/raci", "RACI assignment added.");
    }

    @PostMapping("/raci/{id}/delete")
    public String deleteRaci(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        if (!raci.existsById(id)) return fail(ra, "/raci", "RACI entry not found.");
        raci.deleteById(id);
        logger.log(req, "DELETE", "RaciEntry", id, "Removed RACI entry " + id);
        return ok(ra, "/raci", "RACI assignment removed.");
    }


}
