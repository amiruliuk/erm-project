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

/** Resource planning and allocation. */
@Controller
@RequiredArgsConstructor
public class ResourceController {

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final UnitRepository units;
    private final UnitService unitService;
    private final ResourceAllocationRepository resourceAllocations;
    private final AllocationService allocationService;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- resource planning and allocation

    @GetMapping("/resources")
    public String resources(@RequestParam(required = false) Long unitId, Model m) {
        m.addAttribute("allocations", unitId == null ? resourceAllocations.findAll() : resourceAllocations.findByEmployeeUnitId(unitId));
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("projects", projects.findAll());
        return "resources";
    }

    @PostMapping("/resources")
    public String allocateResource(@RequestParam Long employeeId,
                                   @RequestParam Long projectId,
                                   @RequestParam LocalDate startDate,
                                   @RequestParam LocalDate endDate,
                                   @RequestParam BigDecimal allocationPercent,
                                   @RequestParam(required = false) String notes,
                                   HttpServletRequest req,
                                   RedirectAttributes ra) {
        String error = allocationService.allocate(employeeId, projectId, startDate, endDate, allocationPercent, notes);
        if (error != null) return fail(ra, "/resources", error);
        logger.log(req, "ALLOCATE", "ResourceAllocation", null,
                "Employee " + employeeId + " -> project " + projectId + " at " + allocationPercent + "% ("
                        + startDate + " to " + endDate + ")");
        return ok(ra, "/resources", "Resource allocation saved.");
    }

    @PostMapping("/resources/{id}/delete")
    public String deleteAllocation(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        if (!resourceAllocations.existsById(id)) return fail(ra, "/resources", "Allocation not found.");
        resourceAllocations.deleteById(id);
        logger.log(req, "DELETE", "ResourceAllocation", id, "Removed allocation " + id);
        return ok(ra, "/resources", "Resource allocation removed.");
    }


}
