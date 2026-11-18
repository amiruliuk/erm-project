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

/** Employee register: read = staff roles, write = ADMIN (URL rules in SecurityConfig). */
@Controller
@RequiredArgsConstructor
public class EmployeeController {
    private static final List<String> EMPLOYMENT_STATUSES = List.of("ACTIVE", "ON_LEAVE", "INACTIVE");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final UnitRepository units;
    private final UnitService unitService;
    private final FileStorageService storage;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- employees (read: staff roles, write: ADMIN)

    @GetMapping("/employees")
    public String employees(@RequestParam(required = false) Long unitId,
                            @RequestParam(required = false) Long departmentId, Model m) {
        m.addAttribute("employees", unitId != null ? employees.findByUnitId(unitId)
                : departmentId != null ? employees.findByDepartmentId(departmentId) : employees.findAll());
        m.addAttribute("departments", departments.findAll());
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        m.addAttribute("departmentId", departmentId);
        m.addAttribute("employmentStatuses", EMPLOYMENT_STATUSES);
        return "employees";
    }

@PostMapping("/employees")
public String saveEmployee(@RequestParam String employeeCode,
                           @RequestParam String fullName,
                           @RequestParam String email,
                           @RequestParam(required = false) String jobTitle,
                           @RequestParam(required = false) Long departmentId,
                           @RequestParam(required = false) Long unitId,
                           @RequestParam(required = false) MultipartFile photo,
                           HttpServletRequest req,
                           RedirectAttributes ra) {
    if (isBlank(employeeCode) || isBlank(fullName) || isBlank(email)) {
        return fail(ra, "/employees", "Employee code, full name and email are required.");
    }
    var e = new Employee();
    e.setEmployeeCode(employeeCode.trim());
    e.setFullName(fullName.trim());
    e.setEmail(email.trim());
    e.setJobTitle(blankToNull(jobTitle));
    e.setEmploymentStatus("ACTIVE");
    String assignError = unitService.assign(e, departmentId, unitId);
    if (assignError != null) return fail(ra, "/employees", assignError);
    try {
        employees.save(e);
    } catch (DataIntegrityViolationException ex) {
        return fail(ra, "/employees", "An employee with that code or email already exists.");
    }
    logger.log(req, "CREATE", "Employee", e.getId(), "Added employee " + e.getEmployeeCode());

    if (photo != null && !photo.isEmpty()) {
        try {
            e.setPhotoFileName(storage.save(photo));
            e.setPhotoContentType(photo.getContentType());
            employees.save(e);
        } catch (IllegalArgumentException | IOException ex) {
            return fail(ra, "/employees", "Employee added, but the photo was rejected: " + ex.getMessage());
        }
    }
    return ok(ra, "/employees", "Employee added.");
}

    @PostMapping("/employees/{id}/status")
    public String updateEmployeeStatus(@PathVariable Long id, @RequestParam String status,
                                       HttpServletRequest req, RedirectAttributes ra) {
        var e = employees.findById(id).orElse(null);
        if (e == null) return fail(ra, "/employees", "Employee not found.");
        if (!EMPLOYMENT_STATUSES.contains(status)) return fail(ra, "/employees", "Invalid employment status.");
        String old = e.getEmploymentStatus();
        e.setEmploymentStatus(status);
        employees.save(e);
        logger.log(req, "STATUS_CHANGE", "Employee", id, e.getEmployeeCode() + " status " + old + " -> " + status);
        return ok(ra, "/employees", "Employment status updated. Only ACTIVE employees can be allocated or recommended.");
    }

    /** Move an employee to another department / unit (ADMIN, see SecurityConfig). */
    @PostMapping("/employees/{id}/assign")
    public String assignEmployee(@PathVariable Long id,
                                 @RequestParam(required = false) Long departmentId,
                                 @RequestParam(required = false) Long unitId,
                                 HttpServletRequest req, RedirectAttributes ra) {
        var e = employees.findById(id).orElse(null);
        if (e == null) return fail(ra, "/employees", "Employee not found.");
        String err = unitService.assign(e, departmentId, unitId);
        if (err != null) return fail(ra, "/employees", err);
        employees.save(e);
        logger.log(req, "UPDATE", "Employee", id, e.getEmployeeCode() + " moved to department "
                + (departmentId == null ? "-" : departmentId) + ", unit " + (unitId == null ? "-" : unitId));
        return ok(ra, "/employees", "Department / unit updated.");
    }

    @PostMapping("/employees/{id}/photo")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam("photo") MultipartFile photo,
                              HttpServletRequest req,
                              RedirectAttributes ra) {
        var e = employees.findById(id).orElse(null);
        if (e == null) return fail(ra, "/employees", "Employee not found.");
        try {
            String old = e.getPhotoFileName();
            e.setPhotoFileName(storage.save(photo));
            e.setPhotoContentType(photo.getContentType());
            employees.save(e);
            storage.delete(old);
        } catch (IllegalArgumentException | IOException ex) {
            return fail(ra, "/employees", ex.getMessage());
        }
        logger.log(req, "UPDATE", "Employee", id, "Changed photo for " + e.getEmployeeCode());
        return ok(ra, "/employees", "Photo updated.");
    }

    @GetMapping("/employees/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable Long id) throws IOException {
        var e = employees.findById(id).orElse(null);
        if (e == null || e.getPhotoFileName() == null) return ResponseEntity.notFound().build();
        Resource r = storage.load(e.getPhotoFileName());
        if (!r.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(FileStorageService.mediaTypeFor(e.getPhotoFileName()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(r);
    }

    @PostMapping("/employees/{id}/photo/delete")
    public String deletePhoto(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        var e = employees.findById(id).orElse(null);
        if (e == null) return fail(ra, "/employees", "Employee not found.");
        try {
            storage.delete(e.getPhotoFileName());
        } catch (IOException ex) {
            return fail(ra, "/employees", "Could not delete the photo file.");
        }
        e.setPhotoFileName(null);
        e.setPhotoContentType(null);
        employees.save(e);
        logger.log(req, "UPDATE", "Employee", id, "Removed photo for " + e.getEmployeeCode());
        return ok(ra, "/employees", "Photo removed.");
    }


}
