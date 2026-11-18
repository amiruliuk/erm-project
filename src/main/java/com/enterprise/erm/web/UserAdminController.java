package com.enterprise.erm.web;

import com.enterprise.erm.repository.AppUserRepository;
import com.enterprise.erm.repository.EmployeeRepository;
import com.enterprise.erm.service.AuditService;
import com.enterprise.erm.service.CurrentUserService;
import com.enterprise.erm.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * /users/** is ADMIN-only (SecurityConfig). /account is for any signed-in user.
 * Passwords are never written to the audit log.
 */
@Controller
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService service;
    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    @GetMapping("/users")
    public String list(Model m) {
        m.addAttribute("users", users.findAllByOrderByUsernameAsc());
        m.addAttribute("roles", UserAdminService.ROLES);
        m.addAttribute("employees", employees.findAll());
        return "users";
    }

    @PostMapping("/users")
    public String create(@RequestParam String username, @RequestParam String password, @RequestParam String role,
                         @RequestParam(required = false) String employeeCode,
                         HttpServletRequest req, RedirectAttributes ra) {
        String err = service.create(username, password, role, employeeCode);
        if (err != null) return fail(ra, "/users", err);
        log(req, "CREATE", "AppUser", "Created login " + username.trim() + " with " + role);
        return ok(ra, "/users", "Login created.");
    }

    @PostMapping("/users/{id}/role")
    public String role(@PathVariable Long id, @RequestParam String role, HttpServletRequest req, RedirectAttributes ra) {
        String err = service.changeRole(id, role, currentUser.username());
        if (err != null) return fail(ra, "/users", err);
        log(req, "UPDATE", "AppUser", "User id " + id + " role set to " + role);
        return ok(ra, "/users", "Role updated. It applies at the user's next sign-in.");
    }

    @PostMapping("/users/{id}/enabled")
    public String enabled(@PathVariable Long id, @RequestParam boolean enabled, HttpServletRequest req, RedirectAttributes ra) {
        String err = service.setEnabled(id, enabled, currentUser.username());
        if (err != null) return fail(ra, "/users", err);
        log(req, "UPDATE", "AppUser", "User id " + id + (enabled ? " enabled" : " disabled"));
        return ok(ra, "/users", enabled ? "Login enabled." : "Login disabled.");
    }

    @PostMapping("/users/{id}/employee")
    public String link(@PathVariable Long id, @RequestParam(required = false) String employeeCode,
                       HttpServletRequest req, RedirectAttributes ra) {
        String err = service.linkEmployee(id, employeeCode);
        if (err != null) return fail(ra, "/users", err);
        log(req, "UPDATE", "AppUser", "User id " + id + " linked to employee " + employeeCode);
        return ok(ra, "/users", "Employee link saved.");
    }

    @PostMapping("/users/{id}/password")
    public String reset(@PathVariable Long id, @RequestParam String password, HttpServletRequest req, RedirectAttributes ra) {
        String err = service.resetPassword(id, password);
        if (err != null) return fail(ra, "/users", err);
        log(req, "UPDATE", "AppUser", "Password reset for user id " + id);
        return ok(ra, "/users", "Password reset.");
    }

    // ---------------------------------------------------------------- own account

    @GetMapping("/account")
    public String account() {
        return "account";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, HttpServletRequest req, RedirectAttributes ra) {
        String err = service.changeOwnPassword(currentUser.username(), currentPassword, newPassword, confirmPassword);
        if (err != null) return fail(ra, "/account", err);
        log(req, "UPDATE", "AppUser", "Changed own password");
        return ok(ra, "/account", "Password changed.");
    }

    private void log(HttpServletRequest req, String action, String type, String details) {
        try {
            audit.record(currentUser.username(), action, type, null, details, req.getRemoteAddr());
        } catch (Exception ignored) {
            // auditing must never break the action itself
        }
    }

    private static String ok(RedirectAttributes ra, String path, String msg) {
        ra.addFlashAttribute("flashSuccess", msg);
        return "redirect:" + path;
    }

    private static String fail(RedirectAttributes ra, String path, String msg) {
        ra.addFlashAttribute("flashError", msg);
        return "redirect:" + path;
    }
}
