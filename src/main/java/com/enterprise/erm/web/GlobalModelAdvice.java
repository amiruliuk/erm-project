package com.enterprise.erm.web;

import com.enterprise.erm.repository.ApprovalRepository;
import com.enterprise.erm.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds who-is-signed-in and what-they-may-do flags to every page model.
 * Templates use these only to HIDE controls; the real enforcement is in SecurityConfig and the controllers.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrentUserService user;
    private final ApprovalRepository approvals;

    @ModelAttribute
    public void common(Model m, HttpServletRequest req) {
        if (!user.isAuthenticated()) return;

        boolean admin = user.hasRole("ADMIN");
        boolean manager = user.hasRole("MANAGER");
        boolean approver = user.hasRole("APPROVER");
        boolean auditor = user.hasRole("AUDITOR");
        boolean seesPeopleData = admin || manager || approver || auditor;

        String name = user.username();
        m.addAttribute("currentUser", name);
        m.addAttribute("currentRole", user.roleLabel());
        m.addAttribute("initials", name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase());
        m.addAttribute("currentPath", req.getRequestURI());
        m.addAttribute("canAdmin", admin);
        m.addAttribute("canManage", admin || manager);
        m.addAttribute("canApprove", approver);
        m.addAttribute("canViewPeople", seesPeopleData);
        m.addAttribute("canAudit", admin || auditor);

        // NEW: CBS governance pages (skill matrix, backup roster, reviews, evidence)
        m.addAttribute("canGovern", seesPeopleData);

        if (seesPeopleData) m.addAttribute("pendingBadge", approvals.countByStatus("PENDING"));
    }
}