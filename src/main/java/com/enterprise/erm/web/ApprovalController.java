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

/** Approval requests (maker) and decisions (APPROVER only, maker-checker). */
@Controller
@RequiredArgsConstructor
public class ApprovalController {
    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
    private static final List<String> APPROVAL_TYPES = List.of("PROJECT_START", "PROJECT_HOLD", "CHANGE_REQUEST",
            "SECURITY_REVIEW", "PROJECT_CLOSURE", "BUDGET");

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final ApprovalRepository approvals;
    private final CurrentUserService currentUser;
    private final ActionLogger logger;

    @GetMapping("/approvals")
    public String approvals(Model m) {
        m.addAttribute("approvals", approvals.findAll());
        m.addAttribute("projects", projects.findAll());
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("approvalTypes", APPROVAL_TYPES);
        return "approvals";
    }

    /** Raise an approval request (maker). The decision is taken separately by an APPROVER (checker). */
    @PostMapping("/approvals/request")
    public String requestApproval(@RequestParam Long projectId,
                                  @RequestParam String approvalType,
                                  @RequestParam(required = false) Long approverId,
                                  @RequestParam(required = false) String comments,
                                  HttpServletRequest req, RedirectAttributes ra) {
        var me = currentUser.employee().orElse(null);
        if (me == null)
            return fail(ra, "/approvals", "Your login is not linked to an employee record, so you cannot raise requests.");
        var project = projects.findById(projectId).orElse(null);
        if (project == null || !APPROVAL_TYPES.contains(approvalType))
            return fail(ra, "/approvals", "Select a valid project and request type.");
        var a = new Approval();
        a.setProject(project);
        a.setRequestedBy(me);
        a.setApprovalType(approvalType);
        a.setComments(blankToNull(comments));
        if (approverId != null) {
            var approver = employees.findById(approverId).orElse(null);
            if (approver == null) return fail(ra, "/approvals", "Selected approver no longer exists.");
            if (approver.getId().equals(me.getId())) return fail(ra, "/approvals", "You cannot name yourself as the approver.");
            a.setApprover(approver);
        }
        approvals.save(a);
        logger.log(req, "REQUEST", "Approval", a.getId(), approvalType + " for project " + project.getProjectCode());
        return ok(ra, "/approvals", "Approval request raised.");
    }

    @PostMapping("/approvals/{id}/decision")
    public String decision(@PathVariable Long id,
                           @RequestParam String status,
                           @RequestParam(required = false) String comments,
                           HttpServletRequest req,
                           RedirectAttributes ra) {
        if (!DECISIONS.contains(status)) return fail(ra, "/approvals", "Invalid decision.");
        var a = approvals.findById(id).orElse(null);
        if (a == null) return fail(ra, "/approvals", "Approval request not found.");
        if (!"PENDING".equalsIgnoreCase(a.getStatus()))
            return fail(ra, "/approvals", "This request has already been decided.");

        // Fail closed: we must know which employee is deciding.
        var me = currentUser.employee().orElse(null);
        if (me == null)
            return fail(ra, "/approvals", "Your login is not linked to an employee record, so you cannot approve requests.");

        // Maker-checker: nobody approves a request they raised themselves.
        if (a.getRequestedBy() != null && a.getRequestedBy().getId().equals(me.getId())) {
            logger.log(req, "APPROVAL_BLOCKED", "Approval", id, "Self-approval attempt blocked");
            return fail(ra, "/approvals", "You cannot decide a request that you raised yourself (maker-checker rule).");
        }
        // If a specific approver was named, only that person may decide.
        if (a.getApprover() != null && !a.getApprover().getId().equals(me.getId()))
            return fail(ra, "/approvals", "This request is assigned to another approver.");

        if ("REJECTED".equals(status) && isBlank(comments))
            return fail(ra, "/approvals", "A comment is required when rejecting a request.");

        a.setApprover(me);
        a.setStatus(status);
        a.setComments(mergeComments(a.getComments(), blankToNull(comments)));
        approvals.save(a);
        logger.log(req, "APPROVED".equals(status) ? "APPROVE" : "REJECT", "Approval", id,
                a.getApprovalType() + " for project " + (a.getProject() == null ? "?" : a.getProject().getProjectCode())
                        + " -> " + status);
        return ok(ra, "/approvals", "Decision recorded.");
    }


        /** Keeps the requester's note and appends the approver's comment (column limit is 2000). */
    private static String mergeComments(String requestNote, String decisionNote) {
        if (isBlank(requestNote)) return decisionNote;
        if (decisionNote == null) return requestNote;
        String merged = requestNote + " | Decision: " + decisionNote;
        return merged.length() > 2000 ? merged.substring(0, 2000) : merged;
    }

}
