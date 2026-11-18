package com.enterprise.erm.web;

import com.enterprise.erm.domain.Unit;
import com.enterprise.erm.repository.DepartmentRepository;
import com.enterprise.erm.repository.EmployeeRepository;
import com.enterprise.erm.service.AuditService;
import com.enterprise.erm.service.CurrentUserService;
import com.enterprise.erm.service.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Units live inside departments. URL permissions are in SecurityConfig:
 * read = ADMIN / MANAGER / APPROVER / AUDITOR, write = ADMIN / MANAGER.
 */
@Controller
@RequestMapping("/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    @GetMapping
    public String list(@RequestParam(required = false) Long departmentId, Model m) {
        var all = unitService.findAll().stream()
                .filter(u -> departmentId == null || u.getDepartment().getId().equals(departmentId)).toList();
        m.addAttribute("units", all);
        m.addAttribute("departments", departments.findAll());
        m.addAttribute("departmentId", departmentId);
        m.addAttribute("memberCounts", unitService.memberCounts());
        return "units/list";
    }

    @GetMapping("/{id}")
    public String workspace(@PathVariable Long id, Model m, RedirectAttributes ra) {
        try {
            m.addAttribute("ws", unitService.workspace(id));
        } catch (IllegalArgumentException ex) {
            return fail(ra, ex.getMessage());
        }
        return "units/workspace";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long departmentId, Model m) {
        Unit u = new Unit();
        if (departmentId != null) departments.findById(departmentId).ifPresent(u::setDepartment);
        return form(m, u, "New unit");
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model m, RedirectAttributes ra) {
        try {
            return form(m, unitService.findById(id), "Edit unit");
        } catch (IllegalArgumentException ex) {
            return fail(ra, ex.getMessage());
        }
    }

    @PostMapping
    public String create(@RequestParam String name, @RequestParam(required = false) String code,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) Long unitHeadId,
                         @RequestParam(defaultValue = "false") boolean active,
                         HttpServletRequest req, RedirectAttributes ra) {
        try {
            Unit u = unitService.create(name, code, description, departmentId, unitHeadId, active);
            log(req, "CREATE", u.getId(), "Created unit " + u.getName());
            ra.addFlashAttribute("flashSuccess", "Unit created.");
            return "redirect:/units/" + u.getId();
        } catch (IllegalArgumentException ex) {
            return fail(ra, ex.getMessage(), "/units/new" + (departmentId == null ? "" : "?departmentId=" + departmentId));
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String name, @RequestParam(required = false) String code,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) Long unitHeadId,
                         @RequestParam(defaultValue = "false") boolean active,
                         HttpServletRequest req, RedirectAttributes ra) {
        try {
            Unit u = unitService.update(id, name, code, description, departmentId, unitHeadId, active);
            log(req, "UPDATE", id, "Updated unit " + u.getName());
            ra.addFlashAttribute("flashSuccess", "Unit updated.");
            return "redirect:/units/" + id;
        } catch (IllegalArgumentException ex) {
            return fail(ra, ex.getMessage(), "/units/" + id + "/edit");
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        try {
            unitService.delete(id);
            log(req, "DELETE", id, "Deleted unit " + id);
            ra.addFlashAttribute("flashSuccess", "Unit deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("flashError", ex.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            ra.addFlashAttribute("flashError", "This unit is still referenced by other records. Mark it inactive instead.");
        }
        return "redirect:/units";
    }

    // ------------------------------------------------------------------ helpers

    private String form(Model m, Unit u, String title) {
        m.addAttribute("unit", u);
        m.addAttribute("pageTitle", title);
        m.addAttribute("departments", departments.findAll());
        m.addAttribute("employees", employees.findAll());
        return "units/form";
    }

    private void log(HttpServletRequest req, String action, Long id, String details) {
        try {
            audit.record(currentUser.username(), action, "Unit", id, details, req.getRemoteAddr());
        } catch (Exception ignored) {
            // auditing must never break the action itself
        }
    }

    private static String fail(RedirectAttributes ra, String msg) { return fail(ra, msg, "/units"); }

    private static String fail(RedirectAttributes ra, String msg, String path) {
        ra.addFlashAttribute("flashError", msg);
        return "redirect:" + path;
    }
}
