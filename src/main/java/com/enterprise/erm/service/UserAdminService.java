package com.enterprise.erm.service;

import com.enterprise.erm.domain.AppUser;
import com.enterprise.erm.repository.AppUserRepository;
import com.enterprise.erm.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Login administration. Every method returns null on success or a user-facing error message.
 * Guards: no self-lockout (cannot disable or demote yourself) and the last enabled ADMIN cannot be removed.
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    public static final List<String> ROLES =
            List.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_APPROVER", "ROLE_AUDITOR", "ROLE_USER");

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final PasswordEncoder encoder;

    /** Password policy: at least 12 characters with upper case, lower case and a digit. */
    public static String passwordProblem(String pw) {
        if (pw == null || pw.length() < 12) return "Password must be at least 12 characters.";
        if (pw.length() > 100) return "Password is too long.";
        if (!pw.chars().anyMatch(Character::isUpperCase) || !pw.chars().anyMatch(Character::isLowerCase)
                || !pw.chars().anyMatch(Character::isDigit))
            return "Password needs upper-case and lower-case letters and a digit.";
        return null;
    }

    @Transactional
    public String create(String username, String password, String role, String employeeCode) {
        if (username == null || !USERNAME.matcher(username.trim()).matches())
            return "Username must be 3-50 characters: letters, digits, dot, dash or underscore.";
        String name = username.trim();
        if (users.existsByUsername(name)) return "That username is already taken.";
        if (!ROLES.contains(role)) return "Choose a valid role.";
        String pwError = passwordProblem(password);
        if (pwError != null) return pwError;
        String code = blankToNull(employeeCode);
        if (code != null && employees.findByEmployeeCode(code).isEmpty()) return "No employee with code " + code + ".";

        var u = new AppUser();
        u.setUsername(name);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setEmployeeCode(code);
        users.save(u);
        return null;
    }

    @Transactional
    public String setEnabled(Long id, boolean enabled, String actingUsername) {
        var u = users.findById(id).orElse(null);
        if (u == null) return "User not found.";
        if (!enabled) {
            if (u.getUsername().equals(actingUsername)) return "You cannot disable your own login.";
            if (isLastAdmin(u)) return "At least one enabled administrator must remain.";
        }
        u.setEnabled(enabled);
        users.save(u);
        return null;
    }

    @Transactional
    public String changeRole(Long id, String role, String actingUsername) {
        var u = users.findById(id).orElse(null);
        if (u == null) return "User not found.";
        if (!ROLES.contains(role)) return "Choose a valid role.";
        if (!role.equals(u.getRole())) {
            if (u.getUsername().equals(actingUsername)) return "You cannot change your own role.";
            if (isLastAdmin(u)) return "At least one enabled administrator must remain.";
        }
        u.setRole(role);
        users.save(u);
        return null;
    }

    @Transactional
    public String linkEmployee(Long id, String employeeCode) {
        var u = users.findById(id).orElse(null);
        if (u == null) return "User not found.";
        String code = blankToNull(employeeCode);
        if (code != null && employees.findByEmployeeCode(code).isEmpty()) return "No employee with code " + code + ".";
        u.setEmployeeCode(code);
        users.save(u);
        return null;
    }

    @Transactional
    public String resetPassword(Long id, String newPassword) {
        var u = users.findById(id).orElse(null);
        if (u == null) return "User not found.";
        String pwError = passwordProblem(newPassword);
        if (pwError != null) return pwError;
        u.setPassword(encoder.encode(newPassword));
        users.save(u);
        return null;
    }

    /** Self-service change; requires the current password. */
    @Transactional
    public String changeOwnPassword(String username, String current, String next, String confirm) {
        var u = users.findByUsername(username).orElse(null);
        if (u == null) return "Your password is managed by the directory (LDAP), not by this application.";
        if (current == null || !encoder.matches(current, u.getPassword())) return "Current password is incorrect.";
        if (next == null || !next.equals(confirm)) return "The new passwords do not match.";
        if (next.equals(current)) return "Choose a password different from the current one.";
        String pwError = passwordProblem(next);
        if (pwError != null) return pwError;
        u.setPassword(encoder.encode(next));
        users.save(u);
        return null;
    }

    private boolean isLastAdmin(AppUser u) {
        return "ROLE_ADMIN".equals(u.getRole()) && u.isEnabled() && users.countByRoleAndEnabledTrue("ROLE_ADMIN") <= 1;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
