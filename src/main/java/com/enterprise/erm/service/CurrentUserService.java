package com.enterprise.erm.service;

import com.enterprise.erm.domain.Employee;
import com.enterprise.erm.repository.AppUserRepository;
import com.enterprise.erm.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Who is signed in, what roles they hold, and which employee record they are linked to. */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository users;
    private final EmployeeRepository employees;

    private Authentication auth() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) ? null : a;
    }

    public boolean isAuthenticated() { return auth() != null; }

    public String username() {
        Authentication a = auth();
        return a == null ? "anonymous" : a.getName();
    }

    /** @param roles role names WITHOUT the ROLE_ prefix, e.g. "ADMIN" */
    public boolean hasRole(String... roles) {
        Authentication a = auth();
        if (a == null) return false;
        for (GrantedAuthority ga : a.getAuthorities()) {
            for (String r : roles) {
                if (ga.getAuthority().equals("ROLE_" + r)) return true;
            }
        }
        return false;
    }

    /** A plain employee: sees only their own tasks, skills and RACI entries. */
    public boolean isRestricted() {
        return !hasRole("ADMIN", "MANAGER", "APPROVER", "AUDITOR");
    }

    public String roleLabel() {
        if (hasRole("ADMIN")) return "Administrator";
        if (hasRole("MANAGER")) return "PMO / Manager";
        if (hasRole("APPROVER")) return "Approver";
        if (hasRole("AUDITOR")) return "Auditor (read-only)";
        return "Employee";
    }

    /** The employee record linked to the signed-in login, if any. */
    public Optional<Employee> employee() {
        if (auth() == null) return Optional.empty();
        return users.findByUsername(username())
                .map(u -> u.getEmployeeCode())
                .flatMap(code -> code == null || code.isBlank() ? Optional.<Employee>empty() : employees.findByEmployeeCode(code));
    }
}
