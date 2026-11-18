package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds tabular reports as plain strings. Runs inside a read-only transaction so lazy
 * associations (employee.department etc.) can be read even though open-in-view is off.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    public record Report(String key, String title, String description,
                         List<String> headers, List<List<String>> rows) {}

    public static final List<String> WORK_STATUSES = List.of("OPEN", "IN_PROGRESS", "DONE");
    public static final List<String> CREDENTIAL_STATUSES = List.of("PENDING", "VERIFIED", "REJECTED");
    public static final List<String> APPROVAL_STATUSES = List.of("PENDING", "APPROVED", "REJECTED");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EmployeeSkillRepository employeeSkills;
    private final WorkItemRepository workItems;
    private final ApprovalRepository approvals;
    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final EmployeeCredentialRepository credentials;

    public List<String> statusesFor(String type) {
        return switch (type) {
            case "assignments" -> WORK_STATUSES;
            case "approvals" -> APPROVAL_STATUSES;
            case "credentials" -> CREDENTIAL_STATUSES;
            default -> List.of();
        };
    }

    /** @return the report, or null for an unknown type */
    @Transactional(readOnly = true)
    public Report build(String type, String status) {
        return switch (type) {
            case "skills" -> skills();
            case "assignments" -> assignments(status);
            case "approvals" -> approvalReport(status);
            case "pending-approvals" -> pendingApprovals();
            case "credentials" -> credentialReport(status);
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeCount", employees.count());
        m.put("projectCount", projects.count());
        m.put("workCount", workItems.count());
        m.put("approvalCount", approvals.countByStatus("PENDING"));
        m.put("skillsAssessed", employeeSkills.count());
        m.put("credentialCount", credentials.count());
        m.put("credentialsPending", credentials.countByVerificationStatus(VerificationStatus.PENDING));
        m.put("assignmentsByStatus", workItems.findAll().stream()
                .collect(Collectors.groupingBy(w -> nz(w.getStatus()), TreeMap::new, Collectors.counting())));
        m.put("approvalsByStatus", approvals.findAll().stream()
                .collect(Collectors.groupingBy(a -> nz(a.getStatus()), TreeMap::new, Collectors.counting())));
        return m;
    }

    // ------------------------------------------------------------------ reports

    private Report skills() {
        List<List<String>> rows = employeeSkills.findAll().stream()
                .sorted(Comparator.comparing((EmployeeSkill es) -> es.getEmployee().getFullName())
                        .thenComparing(es -> es.getSkill().getName()))
                .map(es -> List.of(
                        nz(es.getEmployee().getEmployeeCode()),
                        nz(es.getEmployee().getFullName()),
                        es.getEmployee().getDepartment() == null ? "" : nz(es.getEmployee().getDepartment().getName()),
                        nz(es.getSkill().getName()),
                        nz(es.getSkill().getCategory()),
                        es.getProficiency() + " / 5",
                        date(es.getAssessedOn()),
                        nz(es.getEvidence())))
                .collect(Collectors.toList());
        return new Report("skills", "Skills report",
                "Employee skill proficiency (1 = beginner, 5 = expert).",
                List.of("Emp code", "Employee", "Department", "Skill", "Category", "Proficiency", "Assessed on", "Evidence"),
                rows);
    }

    private Report assignments(String status) {
        LocalDate today = LocalDate.now();
        List<List<String>> rows = workItems.findAll().stream()
                .filter(w -> matches(w.getStatus(), status))
                .sorted(Comparator.comparing((WorkItem w) -> w.getDueDate() == null ? LocalDate.MAX : w.getDueDate()))
                .map(w -> List.of(
                        nz(w.getTitle()),
                        w.getProject() == null ? "" : nz(w.getProject().getProjectCode()) + " - " + nz(w.getProject().getName()),
                        w.getAssignee() == null ? "Unassigned" : nz(w.getAssignee().getFullName()),
                        nz(w.getStatus()),
                        nz(w.getPriority()),
                        date(w.getDueDate()),
                        w.getEstimatedHours() == null ? "" : String.valueOf(w.getEstimatedHours()),
                        isOverdue(w, today) ? "YES" : "No"))
                .collect(Collectors.toList());
        return new Report("assignments", "Job assignment report",
                "All work items with assignee, status and overdue flag.",
                List.of("Work item", "Project", "Assignee", "Status", "Priority", "Due date", "Est. hours", "Overdue"),
                rows);
    }

    private Report approvalReport(String status) {
        List<List<String>> rows = approvals.findAll().stream()
                .filter(a -> matches(a.getStatus(), status))
                .sorted(Comparator.comparing(Approval::getCreatedAt).reversed())
                .map(a -> approvalRow(a, false))
                .collect(Collectors.toList());
        return new Report("approvals", "Approval report",
                "All approval requests and their decisions.",
                List.of("Project", "Type", "Work item", "Requested by", "Approver", "Status", "Comments", "Requested on", "Decided on"),
                rows);
    }

    private Report credentialReport(String status) {
        LocalDate today = LocalDate.now();
        List<List<String>> rows = credentials.findAll().stream()
                .filter(c -> matches(c.getVerificationStatus().name(), status))
                .sorted(Comparator.comparing((EmployeeCredential c) -> c.getExpiryDate() == null ? LocalDate.MAX : c.getExpiryDate()))
                .map(c -> List.of(
                        nz(c.getEmployee().getEmployeeCode()),
                        nz(c.getEmployee().getFullName()),
                        String.valueOf(c.getType()),
                        nz(c.getTitle()),
                        nz(c.getIssuer()),
                        date(c.getIssueDate()),
                        c.getExpiryDate() == null ? "No expiry" : date(c.getExpiryDate()),
                        c.getExpiryDate() == null ? "" : String.valueOf(ChronoUnit.DAYS.between(today, c.getExpiryDate())),
                        c.isExpired() ? "EXPIRED" : c.isExpiringSoon() ? "Expiring soon" : "Valid",
                        c.getVerificationStatus().name()))
                .collect(Collectors.toList());
        return new Report("credentials", "Credentials & expiry report",
                "Academic records, training and certifications, soonest expiry first. Negative days = already expired.",
                List.of("Emp code", "Employee", "Type", "Title", "Issuer", "Issued", "Expires", "Days left", "Validity", "Verification"),
                rows);
    }

    private Report pendingApprovals() {
        LocalDate today = LocalDate.now();
        List<List<String>> rows = approvals.findAll().stream()
                .filter(a -> "PENDING".equalsIgnoreCase(a.getStatus()))
                .sorted(Comparator.comparing(Approval::getCreatedAt)) // oldest first
                .map(a -> {
                    List<String> r = new ArrayList<>(approvalRow(a, true));
                    r.add(String.valueOf(ChronoUnit.DAYS.between(a.getCreatedAt().toLocalDate(), today)));
                    return (List<String>) r;
                })
                .collect(Collectors.toList());
        return new Report("pending-approvals", "Pending approvals report",
                "Requests still waiting for a decision, oldest first.",
                List.of("Project", "Type", "Work item", "Requested by", "Approver", "Status", "Comments", "Requested on", "Days waiting"),
                rows);
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> approvalRow(Approval a, boolean pendingView) {
        boolean pending = "PENDING".equalsIgnoreCase(a.getStatus());
        List<String> r = new ArrayList<>(List.of(
                a.getProject() == null ? "" : nz(a.getProject().getName()),
                nz(a.getApprovalType()),
                a.getWorkItem() == null ? "" : nz(a.getWorkItem().getTitle()),
                a.getRequestedBy() == null ? "" : nz(a.getRequestedBy().getFullName()),
                a.getApprover() == null ? "Not assigned" : nz(a.getApprover().getFullName()),
                nz(a.getStatus()),
                nz(a.getComments()),
                a.getCreatedAt() == null ? "" : a.getCreatedAt().toLocalDate().format(DATE)));
        if (!pendingView) {
            r.add(pending || a.getUpdatedAt() == null ? "" : a.getUpdatedAt().toLocalDate().format(DATE));
        }
        return r;
    }

    private static boolean isOverdue(WorkItem w, LocalDate today) {
        return w.getDueDate() != null && w.getDueDate().isBefore(today)
                && !"DONE".equalsIgnoreCase(w.getStatus()) && !"CANCELLED".equalsIgnoreCase(w.getStatus());
    }

    private static boolean matches(String actual, String filter) {
        return filter == null || filter.isBlank() || filter.equalsIgnoreCase(actual);
    }

    private static String date(LocalDate d) { return d == null ? "" : d.format(DATE); }

    private static String nz(String s) { return s == null ? "" : s; }
}
