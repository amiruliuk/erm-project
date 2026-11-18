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

/** Work items. Managers update any task; an employee only the tasks assigned to them. */
@Controller
@RequiredArgsConstructor
public class WorkController {
    private static final List<String> WORK_STATUSES = List.of("OPEN", "IN_PROGRESS", "DONE");

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final WorkItemRepository workItems;
    private final UnitRepository units;
    private final UnitService unitService;
    private final CurrentUserService currentUser;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- work items (employees see their own)

    @GetMapping("/work")
    public String work(@RequestParam(required = false) Long unitId, Model m) {
        List<WorkItem> shown = currentUser.isRestricted()
                ? currentUser.employee().map(e -> workItems.findByAssigneeId(e.getId())).orElse(List.of())
                : unitId != null ? workItems.findForUnit(unitId) : workItems.findAll();
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        m.addAttribute("workItems", shown);
        m.addAttribute("projects", projects.findAll());
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("workStatuses", WORK_STATUSES);
        m.addAttribute("editableWorkIds", editableWorkIds(shown));
        m.addAttribute("today", LocalDate.now());
        return "work";
    }

    @PostMapping("/work")
    public String saveWork(@RequestParam String title,
                           @RequestParam Long projectId,
                           @RequestParam(required = false) Long assigneeId,
                           @RequestParam(defaultValue = "MEDIUM") String priority,
                           @RequestParam(required = false) LocalDate dueDate,
                           @RequestParam(required = false) Integer estimatedHours,
                           HttpServletRequest req, RedirectAttributes ra) {
        var project = projects.findById(projectId).orElse(null);
        if (isBlank(title) || project == null) {
            return fail(ra, "/work", "A title and a valid project are required.");
        }
        if (estimatedHours != null && (estimatedHours < 0 || estimatedHours > 10000))
            return fail(ra, "/work", "Estimated hours must be between 0 and 10000.");
        var w = new WorkItem();
        w.setTitle(title.trim());
        w.setDueDate(dueDate);
        w.setEstimatedHours(estimatedHours);
        w.setPriority(PRIORITIES.contains(priority) ? priority : "MEDIUM");
        w.setProject(project);
        currentUser.employee().ifPresent(w::setCreatedBy);
        if (assigneeId != null) employees.findById(assigneeId).ifPresent(w::setAssignee);
        workItems.save(w);
        logger.log(req, "CREATE", "WorkItem", w.getId(), "Created task '" + w.getTitle() + "' in " + project.getProjectCode());
        return ok(ra, "/work", "Work item created.");
    }

    /** Managers may update any task; an employee may update only the tasks assigned to them. */
    @PostMapping("/work/{id}/status")
    public String updateWorkStatus(@PathVariable Long id, @RequestParam String status,
                                   HttpServletRequest req, RedirectAttributes ra) {
        if (!WORK_STATUSES.contains(status)) return fail(ra, "/work", "Invalid task status.");
        var w = workItems.findById(id).orElse(null);
        if (w == null) return fail(ra, "/work", "Task not found.");
        if (!canEditWork(w)) {
            logger.log(req, "ACCESS_DENIED", "WorkItem", id, "Attempt to change a task not assigned to the user");
            return fail(ra, "/work", "You can only update tasks assigned to you.");
        }
        String old = w.getStatus();
        w.setStatus(status);
        workItems.save(w);
        logger.log(req, "STATUS_CHANGE", "WorkItem", id, w.getTitle() + " status " + old + " -> " + status);
        return ok(ra, "/work", "Task status updated.");
    }

        private boolean canEditWork(WorkItem w) {
        if (currentUser.hasRole("ADMIN", "MANAGER")) return true;
        if (currentUser.hasRole("AUDITOR")) return false;   // read-only role
        var me = currentUser.employee().orElse(null);
        return me != null && w.getAssignee() != null && w.getAssignee().getId().equals(me.getId());
    }

    private Set<Long> editableWorkIds(List<WorkItem> shown) {
        if (shown == null) return Set.of();
        return shown.stream().filter(this::canEditWork).map(WorkItem::getId).collect(Collectors.toSet());
    }

}
