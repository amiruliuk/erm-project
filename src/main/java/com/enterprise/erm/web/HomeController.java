//package com.enterprise.erm.web;
//
//import com.enterprise.erm.domain.*;
//import com.enterprise.erm.repository.*;
//import com.enterprise.erm.service.AllocationService;
//import com.enterprise.erm.service.AssignmentRecommendationService;
//import com.enterprise.erm.service.AuditService;
//import com.enterprise.erm.service.CurrentUserService;
//import com.enterprise.erm.service.UnitService;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.support.RedirectAttributes;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//import org.springframework.http.ContentDisposition;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import java.nio.charset.StandardCharsets;
//
//import com.enterprise.erm.service.FileStorageService;
//import org.springframework.core.io.Resource;
//import org.springframework.http.CacheControl;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.multipart.MultipartFile;
//import java.io.IOException;
//import java.time.Duration;
//
//
///**
// * URL-level permissions are enforced in SecurityConfig. This controller adds the checks that
// * depend on data: maker-checker on approvals, and "own records only" for plain employees.
// */
//@Controller
//@RequiredArgsConstructor
//public class HomeController {
//
//    private static final Set<String> DECISIONS = Set.of("APPROVED", "REJECTED");
//    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
//    private static final List<String> WORK_STATUSES = List.of("OPEN", "IN_PROGRESS", "DONE");
//    private static final List<String> EMPLOYMENT_STATUSES = List.of("ACTIVE", "ON_LEAVE", "INACTIVE");
//    private static final List<String> APPROVAL_TYPES = List.of("PROJECT_START", "PROJECT_HOLD", "CHANGE_REQUEST",
//            "SECURITY_REVIEW", "PROJECT_CLOSURE", "BUDGET");
//    private static final List<String> PROJECT_STATUSES = List.of("PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED");
//
//    private final EmployeeRepository employees;
//    private final ProjectRepository projects;
//    private final WorkItemRepository workItems;
//    private final ApprovalRepository approvals;
//    private final SkillRepository skills;
//    private final DepartmentRepository departments;
//    private final UnitRepository units;
//    private final UnitService unitService;
//    private final RaciEntryRepository raci;
//    private final EmployeeSkillRepository employeeSkills;
//    private final ResourceAllocationRepository resourceAllocations;
//    private final AssignmentRecommendationService recommendations;
//    private final AllocationService allocationService;
//    private final CurrentUserService currentUser;
//    private final AuditService audit;
//    // add to the fields
//    private final FileStorageService storage;
//    private final EmployeeCredentialRepository credentials;
//
//    // ---------------------------------------------------------------- dashboard / login
//
//    @GetMapping("/")
//    public String dashboard(Model m) {
//        m.addAttribute("employeeCount", employees.count());
//        m.addAttribute("projectCount", projects.count());
//        m.addAttribute("openWork", workItems.countByStatusNot("DONE"));
//        m.addAttribute("pendingApprovals", approvals.countByStatus("PENDING"));
//        m.addAttribute("recentProjects", projects.findAll());
//        m.addAttribute("allocationCount", resourceAllocations.count());
//        return "dashboard";
//    }
//
//    @GetMapping("/login")
//    public String login() {
//        return "login";
//    }
//
//    // ---------------------------------------------------------------- employees (read: staff roles, write: ADMIN)
//
//    @GetMapping("/employees")
//    public String employees(@RequestParam(required = false) Long unitId,
//                            @RequestParam(required = false) Long departmentId, Model m) {
//        m.addAttribute("employees", unitId != null ? employees.findByUnitId(unitId)
//                : departmentId != null ? employees.findByDepartmentId(departmentId) : employees.findAll());
//        m.addAttribute("departments", departments.findAll());
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        m.addAttribute("departmentId", departmentId);
//        m.addAttribute("employmentStatuses", EMPLOYMENT_STATUSES);
//        return "employees";
//    }
//
//@PostMapping("/employees")
//public String saveEmployee(@RequestParam String employeeCode,
//                           @RequestParam String fullName,
//                           @RequestParam String email,
//                           @RequestParam(required = false) String jobTitle,
//                           @RequestParam(required = false) Long departmentId,
//                           @RequestParam(required = false) Long unitId,
//                           @RequestParam(required = false) MultipartFile photo,
//                           HttpServletRequest req,
//                           RedirectAttributes ra) {
//    if (isBlank(employeeCode) || isBlank(fullName) || isBlank(email)) {
//        return fail(ra, "/employees", "Employee code, full name and email are required.");
//    }
//    var e = new Employee();
//    e.setEmployeeCode(employeeCode.trim());
//    e.setFullName(fullName.trim());
//    e.setEmail(email.trim());
//    e.setJobTitle(blankToNull(jobTitle));
//    e.setEmploymentStatus("ACTIVE");
//    String assignError = unitService.assign(e, departmentId, unitId);
//    if (assignError != null) return fail(ra, "/employees", assignError);
//    try {
//        employees.save(e);
//    } catch (DataIntegrityViolationException ex) {
//        return fail(ra, "/employees", "An employee with that code or email already exists.");
//    }
//    log(req, "CREATE", "Employee", e.getId(), "Added employee " + e.getEmployeeCode());
//
//    if (photo != null && !photo.isEmpty()) {
//        try {
//            e.setPhotoFileName(storage.save(photo));
//            e.setPhotoContentType(photo.getContentType());
//            employees.save(e);
//        } catch (IllegalArgumentException | IOException ex) {
//            return fail(ra, "/employees", "Employee added, but the photo was rejected: " + ex.getMessage());
//        }
//    }
//    return ok(ra, "/employees", "Employee added.");
//}
//
//    @PostMapping("/employees/{id}/status")
//    public String updateEmployeeStatus(@PathVariable Long id, @RequestParam String status,
//                                       HttpServletRequest req, RedirectAttributes ra) {
//        var e = employees.findById(id).orElse(null);
//        if (e == null) return fail(ra, "/employees", "Employee not found.");
//        if (!EMPLOYMENT_STATUSES.contains(status)) return fail(ra, "/employees", "Invalid employment status.");
//        String old = e.getEmploymentStatus();
//        e.setEmploymentStatus(status);
//        employees.save(e);
//        log(req, "STATUS_CHANGE", "Employee", id, e.getEmployeeCode() + " status " + old + " -> " + status);
//        return ok(ra, "/employees", "Employment status updated. Only ACTIVE employees can be allocated or recommended.");
//    }
//
//    /** Move an employee to another department / unit (ADMIN, see SecurityConfig). */
//    @PostMapping("/employees/{id}/assign")
//    public String assignEmployee(@PathVariable Long id,
//                                 @RequestParam(required = false) Long departmentId,
//                                 @RequestParam(required = false) Long unitId,
//                                 HttpServletRequest req, RedirectAttributes ra) {
//        var e = employees.findById(id).orElse(null);
//        if (e == null) return fail(ra, "/employees", "Employee not found.");
//        String err = unitService.assign(e, departmentId, unitId);
//        if (err != null) return fail(ra, "/employees", err);
//        employees.save(e);
//        log(req, "UPDATE", "Employee", id, e.getEmployeeCode() + " moved to department "
//                + (departmentId == null ? "-" : departmentId) + ", unit " + (unitId == null ? "-" : unitId));
//        return ok(ra, "/employees", "Department / unit updated.");
//    }
//
//    @PostMapping("/employees/{id}/photo")
//    public String uploadPhoto(@PathVariable Long id,
//                              @RequestParam("photo") MultipartFile photo,
//                              HttpServletRequest req,
//                              RedirectAttributes ra) {
//        var e = employees.findById(id).orElse(null);
//        if (e == null) return fail(ra, "/employees", "Employee not found.");
//        try {
//            String old = e.getPhotoFileName();
//            e.setPhotoFileName(storage.save(photo));
//            e.setPhotoContentType(photo.getContentType());
//            employees.save(e);
//            storage.delete(old);
//        } catch (IllegalArgumentException | IOException ex) {
//            return fail(ra, "/employees", ex.getMessage());
//        }
//        log(req, "UPDATE", "Employee", id, "Changed photo for " + e.getEmployeeCode());
//        return ok(ra, "/employees", "Photo updated.");
//    }
//
//    @GetMapping("/employees/{id}/photo")
//    public ResponseEntity<Resource> photo(@PathVariable Long id) throws IOException {
//        var e = employees.findById(id).orElse(null);
//        if (e == null || e.getPhotoFileName() == null) return ResponseEntity.notFound().build();
//        Resource r = storage.load(e.getPhotoFileName());
//        if (!r.exists()) return ResponseEntity.notFound().build();
//        return ResponseEntity.ok()
//                .contentType(FileStorageService.mediaTypeFor(e.getPhotoFileName()))
//                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
//                .body(r);
//    }
//
//    @PostMapping("/employees/{id}/photo/delete")
//    public String deletePhoto(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
//        var e = employees.findById(id).orElse(null);
//        if (e == null) return fail(ra, "/employees", "Employee not found.");
//        try {
//            storage.delete(e.getPhotoFileName());
//        } catch (IOException ex) {
//            return fail(ra, "/employees", "Could not delete the photo file.");
//        }
//        e.setPhotoFileName(null);
//        e.setPhotoContentType(null);
//        employees.save(e);
//        log(req, "UPDATE", "Employee", id, "Removed photo for " + e.getEmployeeCode());
//        return ok(ra, "/employees", "Photo removed.");
//    }
//
//    // ---------------------------------------------------------------- projects
//
//    @GetMapping("/projects")
//    public String projects(@RequestParam(required = false) Long unitId, Model m) {
//        m.addAttribute("projects", unitId == null ? projects.findAll() : projects.findByOwningUnitId(unitId));
//        m.addAttribute("departments", departments.findAll());
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        m.addAttribute("employees", employees.findAll());
//        m.addAttribute("projectStatuses", PROJECT_STATUSES);
//        return "projects";
//    }
//
//    @PostMapping("/projects")
//    public String saveProject(@RequestParam String projectCode,
//                              @RequestParam String name,
//                              @RequestParam(required = false) String description,
//                              @RequestParam(required = false) Long departmentId,
//                              @RequestParam(required = false) Long unitId,
//                              @RequestParam(required = false) Long projectManagerId,
//                              @RequestParam(required = false) LocalDate startDate,
//                              @RequestParam(required = false) LocalDate endDate,
//                              @RequestParam(defaultValue = "MEDIUM") String priority,
//                              HttpServletRequest req,
//                              RedirectAttributes ra) {
//        if (isBlank(projectCode) || isBlank(name)) {
//            return fail(ra, "/projects", "Project code and name are required.");
//        }
//        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
//            return fail(ra, "/projects", "Project end date cannot be before its start date.");
//        }
//        var p = new Project();
//        p.setProjectCode(projectCode.trim());
//        p.setName(name.trim());
//        p.setDescription(blankToNull(description));
//        p.setStatus("PLANNING");
//        p.setPriority(PRIORITIES.contains(priority) ? priority : "MEDIUM");
//        p.setStartDate(startDate);
//        p.setEndDate(endDate);
//        if (projectManagerId != null) employees.findById(projectManagerId).ifPresent(p::setProjectManager);
//        Department ownerDept = departmentId == null ? null : departments.findById(departmentId).orElse(null);
//        Unit ownerUnit = unitId == null ? null : units.findWithDetailsById(unitId).orElse(null);
//        if (unitId != null && ownerUnit == null) return fail(ra, "/projects", "Selected unit no longer exists.");
//        if (ownerUnit != null) {
//            if (ownerDept == null) ownerDept = ownerUnit.getDepartment();       // a unit implies its department
//            else if (!ownerUnit.getDepartment().getId().equals(ownerDept.getId()))
//                return fail(ra, "/projects", "Unit " + ownerUnit.getName() + " does not belong to the selected department.");
//        }
//        p.setOwningDepartment(ownerDept);
//        p.setOwningUnit(ownerUnit);
//        try {
//            projects.save(p);
//        } catch (DataIntegrityViolationException ex) {
//            return fail(ra, "/projects", "A project with that code already exists.");
//        }
//        log(req, "CREATE", "Project", p.getId(), "Created project " + p.getProjectCode());
//        return ok(ra, "/projects", "Project created.");
//    }
//
//    @PostMapping("/projects/{id}/status")
//    public String updateProjectStatus(@PathVariable Long id,
//                                      @RequestParam String status,
//                                      HttpServletRequest req,
//                                      RedirectAttributes ra) {
//        var project = projects.findById(id).orElse(null);
//        if (project == null) return fail(ra, "/projects", "Project not found.");
//        if (!PROJECT_STATUSES.contains(status)) return fail(ra, "/projects", "Invalid project status.");
//        String old = project.getStatus();
//        project.setStatus(status);
//        projects.save(project);
//        log(req, "STATUS_CHANGE", "Project", id, project.getProjectCode() + " status " + old + " -> " + status);
//        return ok(ra, "/projects", "Project status updated.");
//    }
//
//    // ---------------------------------------------------------------- resource planning and allocation
//
//    @GetMapping("/resources")
//    public String resources(@RequestParam(required = false) Long unitId, Model m) {
//        m.addAttribute("allocations", unitId == null ? resourceAllocations.findAll() : resourceAllocations.findByEmployeeUnitId(unitId));
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        m.addAttribute("employees", employees.findAll());
//        m.addAttribute("projects", projects.findAll());
//        return "resources";
//    }
//
//    @PostMapping("/resources")
//    public String allocateResource(@RequestParam Long employeeId,
//                                   @RequestParam Long projectId,
//                                   @RequestParam LocalDate startDate,
//                                   @RequestParam LocalDate endDate,
//                                   @RequestParam BigDecimal allocationPercent,
//                                   @RequestParam(required = false) String notes,
//                                   HttpServletRequest req,
//                                   RedirectAttributes ra) {
//        String error = allocationService.allocate(employeeId, projectId, startDate, endDate, allocationPercent, notes);
//        if (error != null) return fail(ra, "/resources", error);
//        log(req, "ALLOCATE", "ResourceAllocation", null,
//                "Employee " + employeeId + " -> project " + projectId + " at " + allocationPercent + "% ("
//                        + startDate + " to " + endDate + ")");
//        return ok(ra, "/resources", "Resource allocation saved.");
//    }
//
//    @PostMapping("/resources/{id}/delete")
//    public String deleteAllocation(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
//        if (!resourceAllocations.existsById(id)) return fail(ra, "/resources", "Allocation not found.");
//        resourceAllocations.deleteById(id);
//        log(req, "DELETE", "ResourceAllocation", id, "Removed allocation " + id);
//        return ok(ra, "/resources", "Resource allocation removed.");
//    }
//
//    // ---------------------------------------------------------------- skills (employees see their own)
//
//    @GetMapping("/skills")
//    public String skills(Model m) {
//        m.addAttribute("skills", skills.findAll());
//        m.addAttribute("employees", employees.findAll());
//        m.addAttribute("employeeSkills", currentUser.isRestricted()
//                ? currentUser.employee().map(e -> employeeSkills.findByEmployeeId(e.getId())).orElse(List.of())
//                : employeeSkills.findAll());
//        return "skills";
//    }
//
//    @PostMapping("/skills")
//    public String saveSkill(@RequestParam String name,
//                            @RequestParam(required = false) String category,
//                            HttpServletRequest req, RedirectAttributes ra) {
//        if (isBlank(name)) return fail(ra, "/skills", "Skill name is required.");
//        var s = new Skill();
//        s.setName(name.trim());
//        s.setCategory(blankToNull(category));
//        try {
//            skills.save(s);
//        } catch (DataIntegrityViolationException ex) {
//            return fail(ra, "/skills", "That skill already exists.");
//        }
//        log(req, "CREATE", "Skill", s.getId(), "Added skill " + s.getName());
//        return ok(ra, "/skills", "Skill added.");
//    }
//
//    @PostMapping("/skills/assess")
//    public String assess(@RequestParam Long employeeId,
//                         @RequestParam Long skillId,
//                         @RequestParam int proficiency,
//                         HttpServletRequest req, RedirectAttributes ra) {
//        var employee = employees.findById(employeeId).orElse(null);
//        var skill = skills.findById(skillId).orElse(null);
//        if (employee == null || skill == null) {
//            return fail(ra, "/skills", "Selected employee or skill no longer exists.");
//        }
//        // one assessment per employee+skill (unique constraint) -> update instead of insert
//        var es = employeeSkills.findByEmployeeIdAndSkillId(employeeId, skillId).orElseGet(EmployeeSkill::new);
//        es.setEmployee(employee);
//        es.setSkill(skill);
//        es.setProficiency(Math.max(1, Math.min(5, proficiency)));
//        es.setAssessedOn(LocalDate.now());
//        employeeSkills.save(es);
//        log(req, "ASSESS", "EmployeeSkill", es.getId(), employee.getEmployeeCode() + " / " + skill.getName() + " = " + es.getProficiency());
//        return ok(ra, "/skills", "Assessment saved.");
//    }
//
//    // ---------------------------------------------------------------- RACI (employees see their own entries)
//
//    @GetMapping("/raci")
//    public String raci(@RequestParam(required = false) Long projectId,
//                       @RequestParam(required = false) Long unitId, Model m) {
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        m.addAttribute("projects", projects.findAll());
//        m.addAttribute("employees", employees.findAll());
//        List<RaciEntry> entries;
//        if (currentUser.isRestricted()) {
//            entries = currentUser.employee().map(e -> raci.findByEmployeeId(e.getId())).orElse(List.of());
//        } else {
//            entries = unitId != null ? raci.findByEmployeeUnitId(unitId)
//                    : projectId == null ? raci.findAll() : raci.findByProjectId(projectId);
//        }
//        m.addAttribute("entries", entries);
//        return "raci";
//    }
//
//    @PostMapping("/raci")
//    public String saveRaci(@RequestParam Long projectId,
//                           @RequestParam Long employeeId,
//                           @RequestParam String activity,
//                           @RequestParam RaciEntry.RaciRole role,
//                           HttpServletRequest req, RedirectAttributes ra) {
//        var project = projects.findById(projectId).orElse(null);
//        var employee = employees.findById(employeeId).orElse(null);
//        if (project == null || employee == null || isBlank(activity)) {
//            return fail(ra, "/raci", "Project, employee and activity are required.");
//        }
//        String act = activity.trim();
//        if (raci.existsByProjectIdAndActivityIgnoreCaseAndEmployeeId(projectId, act, employeeId))
//            return fail(ra, "/raci", "That person already has a RACI role on this activity. Remove it first to change it.");
//        if (role == RaciEntry.RaciRole.ACCOUNTABLE
//                && raci.existsByProjectIdAndActivityIgnoreCaseAndRole(projectId, act, RaciEntry.RaciRole.ACCOUNTABLE))
//            return fail(ra, "/raci", "Each activity can have only one Accountable person.");
//        var r = new RaciEntry();
//        r.setProject(project);
//        r.setEmployee(employee);
//        r.setActivity(activity.trim());
//        r.setRole(role);
//        raci.save(r);
//        log(req, "CREATE", "RaciEntry", r.getId(), r.getRole() + ": " + employee.getEmployeeCode() + " on '" + r.getActivity() + "' (" + project.getProjectCode() + ")");
//        return ok(ra, "/raci", "RACI assignment added.");
//    }
//
//    @PostMapping("/raci/{id}/delete")
//    public String deleteRaci(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
//        if (!raci.existsById(id)) return fail(ra, "/raci", "RACI entry not found.");
//        raci.deleteById(id);
//        log(req, "DELETE", "RaciEntry", id, "Removed RACI entry " + id);
//        return ok(ra, "/raci", "RACI assignment removed.");
//    }
//
//    // ---------------------------------------------------------------- recommendations (managers only)
//
//    @GetMapping("/assignments/recommend")
//    public String recommend(@RequestParam(required = false) Long skillId,
//                            @RequestParam(defaultValue = "3") int requiredLevel,
//                            @RequestParam(required = false) Long unitId,
//                            Model m) {
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        List<Skill> allSkills = skills.findAll();
//        if (skillId == null && !allSkills.isEmpty()) skillId = allSkills.get(0).getId();
//        m.addAttribute("recommendations",
//                skillId == null ? List.of() : recommendations.recommend(skillId, requiredLevel, unitId));
//        m.addAttribute("skills", allSkills);
//        m.addAttribute("skillId", skillId);
//        m.addAttribute("requiredLevel", requiredLevel);
//        return "recommendations";
//    }
//
//    // ---------------------------------------------------------------- work items (employees see their own)
//
//    @GetMapping("/work")
//    public String work(@RequestParam(required = false) Long unitId, Model m) {
//        List<WorkItem> shown = currentUser.isRestricted()
//                ? currentUser.employee().map(e -> workItems.findByAssigneeId(e.getId())).orElse(List.of())
//                : unitId != null ? workItems.findForUnit(unitId) : workItems.findAll();
//        m.addAttribute("units", unitService.findAll());
//        m.addAttribute("unitId", unitId);
//        m.addAttribute("workItems", shown);
//        m.addAttribute("projects", projects.findAll());
//        m.addAttribute("employees", employees.findAll());
//        m.addAttribute("workStatuses", WORK_STATUSES);
//        m.addAttribute("editableWorkIds", editableWorkIds(shown));
//        m.addAttribute("today", LocalDate.now());
//        return "work";
//    }
//
//    @PostMapping("/work")
//    public String saveWork(@RequestParam String title,
//                           @RequestParam Long projectId,
//                           @RequestParam(required = false) Long assigneeId,
//                           @RequestParam(defaultValue = "MEDIUM") String priority,
//                           @RequestParam(required = false) LocalDate dueDate,
//                           @RequestParam(required = false) Integer estimatedHours,
//                           HttpServletRequest req, RedirectAttributes ra) {
//        var project = projects.findById(projectId).orElse(null);
//        if (isBlank(title) || project == null) {
//            return fail(ra, "/work", "A title and a valid project are required.");
//        }
//        if (estimatedHours != null && (estimatedHours < 0 || estimatedHours > 10000))
//            return fail(ra, "/work", "Estimated hours must be between 0 and 10000.");
//        var w = new WorkItem();
//        w.setTitle(title.trim());
//        w.setDueDate(dueDate);
//        w.setEstimatedHours(estimatedHours);
//        w.setPriority(PRIORITIES.contains(priority) ? priority : "MEDIUM");
//        w.setProject(project);
//        currentUser.employee().ifPresent(w::setCreatedBy);
//        if (assigneeId != null) employees.findById(assigneeId).ifPresent(w::setAssignee);
//        workItems.save(w);
//        log(req, "CREATE", "WorkItem", w.getId(), "Created task '" + w.getTitle() + "' in " + project.getProjectCode());
//        return ok(ra, "/work", "Work item created.");
//    }
//
//    /** Managers may update any task; an employee may update only the tasks assigned to them. */
//    @PostMapping("/work/{id}/status")
//    public String updateWorkStatus(@PathVariable Long id, @RequestParam String status,
//                                   HttpServletRequest req, RedirectAttributes ra) {
//        if (!WORK_STATUSES.contains(status)) return fail(ra, "/work", "Invalid task status.");
//        var w = workItems.findById(id).orElse(null);
//        if (w == null) return fail(ra, "/work", "Task not found.");
//        if (!canEditWork(w)) {
//            log(req, "ACCESS_DENIED", "WorkItem", id, "Attempt to change a task not assigned to the user");
//            return fail(ra, "/work", "You can only update tasks assigned to you.");
//        }
//        String old = w.getStatus();
//        w.setStatus(status);
//        workItems.save(w);
//        log(req, "STATUS_CHANGE", "WorkItem", id, w.getTitle() + " status " + old + " -> " + status);
//        return ok(ra, "/work", "Task status updated.");
//    }
//
//    // ---------------------------------------------------------------- approvals (decide: APPROVER only, maker-checker)
//
//    @GetMapping("/approvals")
//    public String approvals(Model m) {
//        m.addAttribute("approvals", approvals.findAll());
//        m.addAttribute("projects", projects.findAll());
//        m.addAttribute("employees", employees.findAll());
//        m.addAttribute("approvalTypes", APPROVAL_TYPES);
//        return "approvals";
//    }
//
//    /** Raise an approval request (maker). The decision is taken separately by an APPROVER (checker). */
//    @PostMapping("/approvals/request")
//    public String requestApproval(@RequestParam Long projectId,
//                                  @RequestParam String approvalType,
//                                  @RequestParam(required = false) Long approverId,
//                                  @RequestParam(required = false) String comments,
//                                  HttpServletRequest req, RedirectAttributes ra) {
//        var me = currentUser.employee().orElse(null);
//        if (me == null)
//            return fail(ra, "/approvals", "Your login is not linked to an employee record, so you cannot raise requests.");
//        var project = projects.findById(projectId).orElse(null);
//        if (project == null || !APPROVAL_TYPES.contains(approvalType))
//            return fail(ra, "/approvals", "Select a valid project and request type.");
//        var a = new Approval();
//        a.setProject(project);
//        a.setRequestedBy(me);
//        a.setApprovalType(approvalType);
//        a.setComments(blankToNull(comments));
//        if (approverId != null) {
//            var approver = employees.findById(approverId).orElse(null);
//            if (approver == null) return fail(ra, "/approvals", "Selected approver no longer exists.");
//            if (approver.getId().equals(me.getId())) return fail(ra, "/approvals", "You cannot name yourself as the approver.");
//            a.setApprover(approver);
//        }
//        approvals.save(a);
//        log(req, "REQUEST", "Approval", a.getId(), approvalType + " for project " + project.getProjectCode());
//        return ok(ra, "/approvals", "Approval request raised.");
//    }
//
//    @PostMapping("/approvals/{id}/decision")
//    public String decision(@PathVariable Long id,
//                           @RequestParam String status,
//                           @RequestParam(required = false) String comments,
//                           HttpServletRequest req,
//                           RedirectAttributes ra) {
//        if (!DECISIONS.contains(status)) return fail(ra, "/approvals", "Invalid decision.");
//        var a = approvals.findById(id).orElse(null);
//        if (a == null) return fail(ra, "/approvals", "Approval request not found.");
//        if (!"PENDING".equalsIgnoreCase(a.getStatus()))
//            return fail(ra, "/approvals", "This request has already been decided.");
//
//        // Fail closed: we must know which employee is deciding.
//        var me = currentUser.employee().orElse(null);
//        if (me == null)
//            return fail(ra, "/approvals", "Your login is not linked to an employee record, so you cannot approve requests.");
//
//        // Maker-checker: nobody approves a request they raised themselves.
//        if (a.getRequestedBy() != null && a.getRequestedBy().getId().equals(me.getId())) {
//            log(req, "APPROVAL_BLOCKED", "Approval", id, "Self-approval attempt blocked");
//            return fail(ra, "/approvals", "You cannot decide a request that you raised yourself (maker-checker rule).");
//        }
//        // If a specific approver was named, only that person may decide.
//        if (a.getApprover() != null && !a.getApprover().getId().equals(me.getId()))
//            return fail(ra, "/approvals", "This request is assigned to another approver.");
//
//        if ("REJECTED".equals(status) && isBlank(comments))
//            return fail(ra, "/approvals", "A comment is required when rejecting a request.");
//
//        a.setApprover(me);
//        a.setStatus(status);
//        a.setComments(mergeComments(a.getComments(), blankToNull(comments)));
//        approvals.save(a);
//        log(req, "APPROVED".equals(status) ? "APPROVE" : "REJECT", "Approval", id,
//                a.getApprovalType() + " for project " + (a.getProject() == null ? "?" : a.getProject().getProjectCode())
//                        + " -> " + status);
//        return ok(ra, "/approvals", "Decision recorded.");
//    }
//
//
//    @GetMapping("/credentials")
//    public String credentials(Model m) {
//        m.addAttribute("credentials", currentUser.isRestricted()
//                ? currentUser.employee().map(e -> credentials.findByEmployeeIdOrderByIssueDateDesc(e.getId())).orElse(List.of())
//                : credentials.findAll());
//        m.addAttribute("employees", currentUser.isRestricted()
//                ? currentUser.employee().map(e -> List.of(e)).orElse(List.of())
//                : employees.findAll());
//        m.addAttribute("skills", skills.findAll());
//        m.addAttribute("myEmployeeId", currentUser.employee().map(Employee::getId).orElse(null));
//        m.addAttribute("types", CredentialType.values());
//        m.addAttribute("levels", EducationLevel.values());
//        return "credentials";
//    }
//
//    @PostMapping("/credentials")
//    public String saveCredential(@RequestParam Long employeeId,
//                                 @RequestParam CredentialType type,
//                                 @RequestParam String title,
//                                 @RequestParam String issuer,
//                                 @RequestParam(required = false) String credentialNumber,
//                                 @RequestParam(required = false) String credentialUrl,
//                                 @RequestParam(required = false) EducationLevel educationLevel,
//                                 @RequestParam(required = false) String fieldOfStudy,
//                                 @RequestParam(required = false) String grade,
//                                 @RequestParam(required = false) Integer durationHours,
//                                 @RequestParam(required = false) LocalDate issueDate,
//                                 @RequestParam(required = false) LocalDate expiryDate,
//                                 @RequestParam(required = false) Long skillId,
//                                 @RequestParam(required = false) MultipartFile document,
//                                 HttpServletRequest req, RedirectAttributes ra) {
//        var me = currentUser.employee().orElse(null);
//        if (currentUser.isRestricted() && (me == null || !me.getId().equals(employeeId)))
//            return fail(ra, "/credentials", "You can only add credentials for yourself.");
//        var employee = employees.findById(employeeId).orElse(null);
//        if (employee == null || isBlank(title) || isBlank(issuer))
//            return fail(ra, "/credentials", "Employee, title and issuer are required.");
//        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate))
//            return fail(ra, "/credentials", "Expiry date cannot be before the issue date.");
//        if (durationHours != null && (durationHours < 0 || durationHours > 100000))
//            return fail(ra, "/credentials", "Duration must be a sensible number of hours.");
//        if (!isBlank(credentialUrl) && !credentialUrl.trim().toLowerCase().matches("https?://.+"))
//            return fail(ra, "/credentials", "Verification URL must start with http:// or https://.");
//
//        var c = new EmployeeCredential();
//        c.setEmployee(employee);
//        c.setType(type);
//        c.setTitle(title.trim());
//        c.setIssuer(issuer.trim());
//        c.setCredentialNumber(blankToNull(credentialNumber));
//        c.setCredentialUrl(blankToNull(credentialUrl));
//        if (type == CredentialType.ACADEMIC_DEGREE || type == CredentialType.ACADEMIC_CERTIFICATE) {
//            c.setEducationLevel(educationLevel);
//            c.setFieldOfStudy(blankToNull(fieldOfStudy));
//            c.setGrade(blankToNull(grade));
//        }
//        c.setDurationHours(durationHours);
//        c.setIssueDate(issueDate);
//        c.setExpiryDate(expiryDate);
//        if (skillId != null) skills.findById(skillId).ifPresent(c::setSkill);
//
//        try {
//            if (document != null && !document.isEmpty()) {
//                c.setDocumentFileName(storage.saveDocument(document));
//                c.setDocumentOriginalName(document.getOriginalFilename());
//                c.setDocumentContentType(document.getContentType());
//                c.setDocumentSize(document.getSize());
//            }
//        } catch (IllegalArgumentException | IOException ex) {
//            return fail(ra, "/credentials", ex.getMessage());
//        }
//        credentials.save(c);
//        log(req, "CREATE", "EmployeeCredential", c.getId(), "Added " + type + " '" + c.getTitle() + "' for " + employee.getEmployeeCode());
//        return ok(ra, "/credentials", "Credential added and awaiting verification.");
//    }
//
//    @PostMapping("/credentials/{id}/verify")
//    public String verify(@PathVariable Long id,
//                         @RequestParam VerificationStatus status,
//                         @RequestParam(required = false) String notes,
//                         HttpServletRequest req, RedirectAttributes ra) {
//        var c = credentials.findWithEmployeeById(id).orElse(null);
//        var me = currentUser.employee().orElse(null);
//        if (c == null || me == null) return fail(ra, "/credentials", "Credential or your employee record not found.");
//        if (status == VerificationStatus.PENDING) return fail(ra, "/credentials", "Invalid decision.");
//        if (c.getEmployee().getId().equals(me.getId()))
//            return fail(ra, "/credentials", "You cannot verify your own credential (maker-checker rule).");
//        if (status == VerificationStatus.REJECTED && isBlank(notes))
//            return fail(ra, "/credentials", "A note is required when rejecting.");
//
//        c.setVerificationStatus(status);
//        c.setVerifiedBy(me);
//        c.setVerifiedOn(LocalDate.now());
//        c.setVerificationNotes(blankToNull(notes));
//        credentials.save(c);
//        log(req, status == VerificationStatus.VERIFIED ? "APPROVE" : "REJECT", "EmployeeCredential", id, c.getTitle() + " -> " + status);
//        return ok(ra, "/credentials", "Verification recorded.");
//    }
//
//    /** The uploaded evidence: the owner and the staff roles may open it; other employees may not. */
//    @GetMapping("/credentials/{id}/document")
//    public ResponseEntity<Resource> credentialDocument(@PathVariable Long id, HttpServletRequest req) throws IOException {
//        var c = credentials.findWithEmployeeById(id).orElse(null);
//        if (c == null || c.getDocumentFileName() == null) return ResponseEntity.notFound().build();
//        var me = currentUser.employee().orElse(null);
//        boolean owner = me != null && c.getEmployee().getId().equals(me.getId());
//        if (currentUser.isRestricted() && !owner) {
//            log(req, "ACCESS_DENIED", "EmployeeCredential", id, "Attempt to open another employee's document");
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }
//        Resource r = storage.load(c.getDocumentFileName());
//        if (!r.exists()) return ResponseEntity.notFound().build();
//        String stored = c.getDocumentFileName();
//        String downloadName = "credential-" + id + stored.substring(stored.lastIndexOf('.'));
//        log(req, "VIEW_DOCUMENT", "EmployeeCredential", id, "Opened document of " + c.getEmployee().getEmployeeCode());
//        return ResponseEntity.ok()
//                .contentType(FileStorageService.mediaTypeFor(stored))
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        ContentDisposition.inline().filename(downloadName, StandardCharsets.UTF_8).build().toString())
//                .cacheControl(CacheControl.noStore())
//                .body(r);
//    }
//
//
//    // ---------------------------------------------------------------- helpers
//
//    private void log(HttpServletRequest req, String action, String type, Long id, String details) {
//        try {
//            audit.record(currentUser.username(), action, type, id, details, req.getRemoteAddr());
//        } catch (Exception ignored) {
//            // auditing must never break the action itself
//        }
//    }
//
//    private boolean canEditWork(WorkItem w) {
//        if (currentUser.hasRole("ADMIN", "MANAGER")) return true;
//        if (currentUser.hasRole("AUDITOR")) return false;   // read-only role
//        var me = currentUser.employee().orElse(null);
//        return me != null && w.getAssignee() != null && w.getAssignee().getId().equals(me.getId());
//    }
//
//    private Set<Long> editableWorkIds(List<WorkItem> shown) {
//        if (shown == null) return Set.of();
//        return shown.stream().filter(this::canEditWork).map(WorkItem::getId).collect(Collectors.toSet());
//    }
//
//    /** Keeps the requester's note and appends the approver's comment (column limit is 2000). */
//    private static String mergeComments(String requestNote, String decisionNote) {
//        if (isBlank(requestNote)) return decisionNote;
//        if (decisionNote == null) return requestNote;
//        String merged = requestNote + " | Decision: " + decisionNote;
//        return merged.length() > 2000 ? merged.substring(0, 2000) : merged;
//    }
//
//    private static boolean isBlank(String s) {
//        return s == null || s.isBlank();
//    }
//
//    private static String blankToNull(String s) {
//        return isBlank(s) ? null : s.trim();
//    }
//
//    private static String ok(RedirectAttributes ra, String path, String msg) {
//        ra.addFlashAttribute("flashSuccess", msg);
//        return "redirect:" + path;
//    }
//
//    private static String fail(RedirectAttributes ra, String path, String msg) {
//        ra.addFlashAttribute("flashError", msg);
//        return "redirect:" + path;
//    }
//
//
//}
