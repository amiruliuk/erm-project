package com.enterprise.erm.web;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.domain.dto.SkillMatrixGroup;
import com.enterprise.erm.domain.dto.SkillMatrixReport;
import com.enterprise.erm.domain.dto.SkillMatrixRow;
import com.enterprise.erm.repository.*;
import com.enterprise.erm.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CBS-GOV-003 governance pages. Read access: ADMIN / MANAGER / APPROVER / AUDITOR.
 * Writes: ADMIN / MANAGER (URL rule in SecurityConfig), except evidence verification (ADMIN / APPROVER / AUDITOR).
 */
@Controller
@RequestMapping("/governance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER','AUDITOR')")
public class CbsGovernanceController {

    private final SkillMatrixService matrix;
    private final BackupRosterRepository rosterRepo;
    private final BackupReadinessTestRepository testRepo;
    private final CrossTrainingPlanRepository planRepo;
    private final ReviewScheduleRepository reviewRepo;
    private final EvidenceRepository evidenceRepo;
    private final EmployeeRepository employees;
    private final SkillRepository skills;
    private final AppUserRepository users;
    private final EvidenceService evidenceService;
    private final AuditService audit;
    private final GovernanceAuditService governanceAuditService;
    private final GovernanceDocxExportService governanceDocxExportService;

    // ---- skill matrix -----------------------------------------------------

    @GetMapping("/skill-matrix")
    public String skillMatrix(@RequestParam(defaultValue = "critical") String scope, Model model) {
        model.addAttribute("report", reportFor(scope));
        model.addAttribute("scope", scope);
        return "governance/skill-matrix";
    }

    /** CSV download (the page already linked to ?format=csv, but nothing handled it). */
    @GetMapping(value = "/skill-matrix", params = "format=csv")
    public ResponseEntity<byte[]> skillMatrixCsv(@RequestParam(defaultValue = "critical") String scope) {
        SkillMatrixReport report = reportFor(scope);
        StringBuilder sb = new StringBuilder("Skill,Critical");
        for (SkillMatrixGroup g : report.groups()) {
            for (String code : g.employeeCodes()) sb.append(',').append(csv(code + " (" + g.departmentName() + ")"));
        }
        sb.append(",At level 3+,Single-person risk\r\n");
        for (SkillMatrixRow row : report.rows()) {
            sb.append(csv(row.skillName())).append(',').append(row.critical() ? "YES" : "NO");
            for (SkillMatrixGroup g : row.groups()) {
                g.cellsForGroup().forEach(c -> sb.append(',').append(c.level()));
            }
            sb.append(',').append(row.peopleAtLevel3Plus()).append(',').append(row.singlePersonRisk() ? "RISK" : "OK").append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"skill-matrix-" + ("all".equals(scope) ? "all" : "critical") + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private SkillMatrixReport reportFor(String scope) {
        return "all".equals(scope) ? matrix.buildMatrix() : matrix.buildCriticalMatrix();
    }

    /** Quotes a CSV field and neutralises spreadsheet formulas (=, +, -, @). */
    private static String csv(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---- backup roster ----------------------------------------------------

    @GetMapping("/backup-roster")
    public String backupRoster(Model model) {
        model.addAttribute("rosters", rosterRepo.findByActivityCriticalTrue());
        model.addAttribute("plans", planRepo.findAll());
        model.addAttribute("criticalSkills", skills.findByCriticalTrue());
        model.addAttribute("employees", activeEmployees());
        return "governance/backup-roster";
    }

    /** Create or replace the roster row of one critical activity (one row per activity). */
    @PostMapping("/roster")
    @Transactional
    public String saveRoster(@RequestParam Long activityId,
                             @RequestParam Long primaryId,
                             @RequestParam(required = false) Long backup1Id,
                             @RequestParam(required = false) Long backup2Id,
                             Authentication auth, RedirectAttributes flash) {
        Skill activity = skills.findById(activityId).orElseThrow();
        if (!activity.isCritical()) return fail(flash, "Only a critical skill can have a backup roster.", "/governance/backup-roster");

        Employee primary = employees.findById(primaryId).orElseThrow();
        Employee b1 = backup1Id == null ? null : employees.findById(backup1Id).orElseThrow();
        Employee b2 = backup2Id == null ? null : employees.findById(backup2Id).orElseThrow();

        List<Employee> everyone = new ArrayList<>(List.of(primary));
        if (b1 != null) everyone.add(b1);
        if (b2 != null) everyone.add(b2);
        if (everyone.stream().map(Employee::getId).distinct().count() != everyone.size())
            return fail(flash, "Primary, backup 1 and backup 2 must be three different people.", "/governance/backup-roster");
        if (b1 == null && b2 == null)
            return fail(flash, "Name at least one backup.", "/governance/backup-roster");
        if (everyone.stream().anyMatch(e -> !"ACTIVE".equals(e.getEmploymentStatus())))
            return fail(flash, "Everyone on the roster must be an ACTIVE employee.", "/governance/backup-roster");

        BackupRoster r = rosterRepo.findByActivityId(activityId).orElseGet(BackupRoster::new);
        boolean created = r.getId() == null;
        r.setActivity(activity);
        r.setPrimaryPerson(primary);
        r.setBackup1(b1);
        r.setBackup2(b2);
        BackupRoster saved = rosterRepo.save(r);
        audit.record(auth.getName(), created ? "CREATE" : "UPDATE", "BackupRoster", saved.getId(),
                "Roster for " + activity.getName() + ": " + primary.getEmployeeCode()
                        + " / " + (b1 == null ? "-" : b1.getEmployeeCode())
                        + " / " + (b2 == null ? "-" : b2.getEmployeeCode()), null);
        flash.addFlashAttribute("flashSuccess", "Roster saved for " + activity.getName() + ".");
        return "redirect:/governance/backup-roster";
    }

    // ---- readiness tests --------------------------------------------------

    @GetMapping("/roster/{id}/test/new")
    public String newTest(@PathVariable Long id, Model model) {
        BackupRoster roster = rosterRepo.findWithDetailsById(id).orElseThrow();
        model.addAttribute("roster", roster);
        List<Employee> backups = new ArrayList<>();
        if (roster.getBackup1() != null) backups.add(roster.getBackup1());
        if (roster.getBackup2() != null) backups.add(roster.getBackup2());
        model.addAttribute("backups", backups);   // only backups may perform the test
        return "governance/readiness-test-form";
    }

    @GetMapping("/roster/{id}/tests")
    public String testHistory(@PathVariable Long id, Model model) {
        model.addAttribute("roster", rosterRepo.findWithDetailsById(id).orElseThrow());
        model.addAttribute("tests", testRepo.findByRosterIdOrderByTestedOnDesc(id));
        return "governance/readiness-history";
    }

    @PostMapping("/roster/{id}/test")
    @Transactional
    public String recordTest(@PathVariable Long id,
                             @RequestParam Long performedById,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testedOn,
                             @RequestParam BackupReadinessTest.Result result,
                             @RequestParam(required = false) String notes,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             Authentication auth, RedirectAttributes flash) {
        BackupRoster roster = rosterRepo.findWithDetailsById(id).orElseThrow();
        String back = "/governance/roster/" + id + "/test/new";

        // performer must be one of the backups, never the primary (CBS-GOV-003 definition)
        boolean isBackup = (roster.getBackup1() != null && Objects.equals(roster.getBackup1().getId(), performedById))
                || (roster.getBackup2() != null && Objects.equals(roster.getBackup2().getId(), performedById));
        if (!isBackup) return fail(flash, "A readiness test must be performed by one of the backups, not the primary.", back);
        if (testedOn.isAfter(LocalDate.now())) return fail(flash, "Test date cannot be in the future.", back);

        BackupReadinessTest test = new BackupReadinessTest();
        test.setRoster(roster);
        test.setPerformedBy(employees.findById(performedById).orElseThrow());
        test.setTestedOn(testedOn);
        test.setResult(result);
        test.setNotes(notes);

        if (file != null && !file.isEmpty()) {
            try {
                test.setEvidence(evidenceService.store(file, "READINESS_TEST_RESULT",
                        "Readiness test — " + roster.getActivity().getName() + " " + testedOn, auth.getName()));
            } catch (IllegalArgumentException ex) {
                return fail(flash, ex.getMessage(), back);
            }
        }

        BackupReadinessTest saved = testRepo.save(test);
        // keep the roster's "latest test" pointer on the newest test date (a back-dated test must not replace it)
        BackupReadinessTest current = roster.getLastTest();
        if (current == null || current.getTestedOn() == null || !testedOn.isBefore(current.getTestedOn())) {
            roster.setLastTest(saved);
            rosterRepo.save(roster);
        }

        audit.record(auth.getName(), "CREATE", "BackupReadinessTest", saved.getId(),
                "Recorded " + result + " readiness test for " + roster.getActivity().getName(), null);
        flash.addFlashAttribute("flashSuccess", "Readiness test recorded.");
        return "redirect:/governance/backup-roster";
    }

    // ---- cross-training plan ---------------------------------------------

    @PostMapping("/plan")
    public String createPlan(@RequestParam Long dependencyId,
                             @RequestParam String action,
                             @RequestParam Long ownerId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
                             @RequestParam(defaultValue = "PLANNED") CrossTrainingPlan.PlanStatus status,
                             Authentication auth, RedirectAttributes flash) {
        String back = "/governance/backup-roster";
        if (targetDate.isBefore(LocalDate.now())) return fail(flash, "Target date must be in the future.", back);
        Skill dependency = skills.findById(dependencyId).orElseThrow();
        if (!dependency.isCritical()) return fail(flash, "A mitigation plan must target a critical skill.", back);
        Employee owner = employees.findById(ownerId).orElseThrow();
        if (!"ACTIVE".equals(owner.getEmploymentStatus())) return fail(flash, "The plan owner must be an ACTIVE employee.", back);

        CrossTrainingPlan plan = new CrossTrainingPlan();
        plan.setDependency(dependency);
        plan.setAction(action.trim());
        plan.setOwner(owner);
        plan.setTargetDate(targetDate);
        plan.setStatus(status);
        CrossTrainingPlan saved = planRepo.save(plan);

        audit.record(auth.getName(), "CREATE", "CrossTrainingPlan", saved.getId(),
                "Created mitigation plan for " + dependency.getName(), null);
        flash.addFlashAttribute("flashSuccess", "Mitigation plan added.");
        return "redirect:" + back;
    }

    @PostMapping("/plan/{id}/status")
    public String updatePlanStatus(@PathVariable Long id,
                                   @RequestParam CrossTrainingPlan.PlanStatus status,
                                   Authentication auth) {
        CrossTrainingPlan p = planRepo.findById(id).orElseThrow();
        p.setStatus(status);
        planRepo.save(p);
        audit.record(auth.getName(), "STATUS_CHANGE", "CrossTrainingPlan", id, "Status -> " + status, null);
        return "redirect:/governance/backup-roster";
    }

    // ---- reviews ----------------------------------------------------------

    @GetMapping("/reviews")
    public String reviews(Model model) {
        model.addAttribute("reviews", reviewRepo.findAll());
        model.addAttribute("employees", activeEmployees());
        return "governance/reviews";
    }

    @GetMapping("/reviews/{id}")
    public String reviewDetail(@PathVariable Long id, Model model) {
        model.addAttribute("review", reviewRepo.findById(id).orElseThrow());
        return "governance/review-detail";
    }

    @PostMapping("/reviews")
    public String scheduleReview(@RequestParam String scope,
                                 @RequestParam Long ownerId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOn,
                                 Authentication auth) {
        ReviewSchedule r = new ReviewSchedule();
        r.setScope(scope.trim());
        r.setOwner(employees.findById(ownerId).orElseThrow());
        r.setDueOn(dueOn);
        ReviewSchedule saved = reviewRepo.save(r);
        audit.record(auth.getName(), "CREATE", "ReviewSchedule", saved.getId(),
                "Scheduled " + scope + " for " + dueOn, null);
        return "redirect:/governance/reviews";
    }

    @PostMapping("/reviews/{id}/complete")
    @Transactional
    public String completeReview(@PathVariable Long id,
                                 @RequestParam ReviewSchedule.Outcome outcome,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate completedOn,
                                 @RequestParam(required = false) String notes,
                                 @RequestParam(value = "evidence", required = false) MultipartFile evidence,
                                 Authentication auth, RedirectAttributes flash) {
        ReviewSchedule r = reviewRepo.findById(id).orElseThrow();
        String back = "/governance/reviews/" + id;
        if (r.getCompletedOn() != null) return fail(flash, "This review is already completed.", "/governance/reviews");
        if (completedOn.isAfter(LocalDate.now())) return fail(flash, "Completion date cannot be in the future.", back);

        if (evidence != null && !evidence.isEmpty()) {
            try {
                // v2 stored the file but dropped the Evidence record; it is now linked to the review
                r.setEvidence(evidenceService.store(evidence, "REVIEW_MINUTES",
                        "Review minutes — " + r.getScope() + " " + completedOn, auth.getName()));
            } catch (IllegalArgumentException ex) {
                return fail(flash, ex.getMessage(), back);
            }
        }
        r.setCompletedOn(completedOn);
        r.setOutcome(outcome);
        r.setNotes(notes);
        reviewRepo.save(r);

        ReviewSchedule next = new ReviewSchedule();
        next.setScope(r.getScope());
        next.setOwner(r.getOwner());
        next.setDueOn(completedOn.plusMonths(6));
        reviewRepo.save(next);

        audit.record(auth.getName(), "COMPLETE", "ReviewSchedule", id,
                "Completed: " + outcome + " · next due " + next.getDueOn(), null);
        return "redirect:/governance/reviews";
    }

    // ---- evidence ---------------------------------------------------------

    @GetMapping("/evidence")
    public String evidence(Model model) {
        model.addAttribute("pending", evidenceRepo.findByVerificationStatus(VerificationStatus.PENDING));
        model.addAttribute("all", evidenceRepo.findAll());
        return "governance/evidence";
    }

    @PostMapping("/evidence")
    @Transactional
    public String uploadEvidence(@RequestParam String title,
                                 @RequestParam Evidence.EvidenceType type,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedOn,
                                 @RequestParam MultipartFile file,
                                 Authentication auth, RedirectAttributes flash) {
        if (recordedOn.isAfter(LocalDate.now())) return fail(flash, "Recorded-on date cannot be in the future.", "/governance/evidence");
        Evidence ev;
        try {
            ev = evidenceService.store(file, type.name(), title.trim(), auth.getName());
        } catch (IllegalArgumentException ex) {
            return fail(flash, ex.getMessage(), "/governance/evidence");
        }
        ev.setRecordedOn(recordedOn);
        evidenceRepo.save(ev);
        audit.record(auth.getName(), "CREATE", "Evidence", ev.getId(), "Uploaded evidence '" + ev.getTitle() + "'", null);
        flash.addFlashAttribute("flashSuccess", "Evidence uploaded and awaiting verification.");
        return "redirect:/governance/evidence";
    }

    @PostMapping("/evidence/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','APPROVER','AUDITOR')")
    public String verify(@PathVariable Long id,
                         @RequestParam VerificationStatus status,
                         @RequestParam(required = false) String notes,
                         Authentication auth, RedirectAttributes flash) {
        Evidence e = evidenceRepo.findById(id).orElseThrow();
        if (e.getRecordedBy().equals(auth.getName()))
            return fail(flash, "You cannot verify evidence you recorded yourself.", "/governance/evidence");
        if (status == VerificationStatus.PENDING)
            return fail(flash, "Choose VERIFIED or REJECTED.", "/governance/evidence");
        e.setVerificationStatus(status);
        e.setVerifiedOn(LocalDate.now());
        e.setVerificationNotes(notes);
        users.findByUsername(auth.getName())
                .flatMap(u -> u.getEmployeeCode() == null ? java.util.Optional.<Employee>empty()
                        : employees.findByEmployeeCode(u.getEmployeeCode()))
                .ifPresent(e::setVerifiedBy);
        evidenceRepo.save(e);
        audit.record(auth.getName(), "VERIFY", "Evidence", id, "Evidence " + status, null);
        return "redirect:/governance/evidence";
    }

    @GetMapping("/executive-summary")
    public String executiveGovernanceDashboard(Model model) {
        model.addAttribute("govSummary", governanceAuditService.getExecutiveGovernanceSummary());
        return "governance/executive-summary";
    }

    @GetMapping("/export/cbs-gov-003.docx")
    public ResponseEntity<byte[]> downloadCbsGov003Docx(Principal principal) throws IOException {
        String reviewer = principal != null ? principal.getName() : "CBS Application Technical Lead";
        byte[] docxBytes = governanceDocxExportService.generateCbsGov003Docx(reviewer, "IT Head - MBPLC");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=CBS-GOV-003_Skill_Matrix_Backup_Roster.docx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docxBytes);
    }

    // ---- helpers ----------------------------------------------------------

    private List<Employee> activeEmployees() {
        return employees.findAll().stream().filter(e -> "ACTIVE".equals(e.getEmploymentStatus())).toList();
    }

    /** Validation problems go back to the page as a message instead of a blank error page. */
    private static String fail(RedirectAttributes flash, String message, String backTo) {
        flash.addFlashAttribute("flashError", message);
        return "redirect:" + backTo;
    }
}
