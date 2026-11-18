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

/** Credentials register: owner or staff roles; verification is ADMIN only with maker-checker. */
@Controller
@RequiredArgsConstructor
public class CredentialController {

    private final EmployeeRepository employees;
    private final SkillRepository skills;
    private final CurrentUserService currentUser;
    private final FileStorageService storage;
    private final EmployeeCredentialRepository credentials;
    private final ActionLogger logger;

    @GetMapping("/credentials")
    public String credentials(Model m) {
        m.addAttribute("credentials", currentUser.isRestricted()
                ? currentUser.employee().map(e -> credentials.findByEmployeeIdOrderByIssueDateDesc(e.getId())).orElse(List.of())
                : credentials.findAll());
        m.addAttribute("employees", currentUser.isRestricted()
                ? currentUser.employee().map(e -> List.of(e)).orElse(List.of())
                : employees.findAll());
        m.addAttribute("skills", skills.findAll());
        m.addAttribute("myEmployeeId", currentUser.employee().map(Employee::getId).orElse(null));
        m.addAttribute("types", CredentialType.values());
        m.addAttribute("levels", EducationLevel.values());
        return "credentials";
    }

    @PostMapping("/credentials")
    public String saveCredential(@RequestParam Long employeeId,
                                 @RequestParam CredentialType type,
                                 @RequestParam String title,
                                 @RequestParam String issuer,
                                 @RequestParam(required = false) String credentialNumber,
                                 @RequestParam(required = false) String credentialUrl,
                                 @RequestParam(required = false) EducationLevel educationLevel,
                                 @RequestParam(required = false) String fieldOfStudy,
                                 @RequestParam(required = false) String grade,
                                 @RequestParam(required = false) Integer durationHours,
                                 @RequestParam(required = false) LocalDate issueDate,
                                 @RequestParam(required = false) LocalDate expiryDate,
                                 @RequestParam(required = false) Long skillId,
                                 @RequestParam(required = false) MultipartFile document,
                                 HttpServletRequest req, RedirectAttributes ra) {
        var me = currentUser.employee().orElse(null);
        if (currentUser.isRestricted() && (me == null || !me.getId().equals(employeeId)))
            return fail(ra, "/credentials", "You can only add credentials for yourself.");
        var employee = employees.findById(employeeId).orElse(null);
        if (employee == null || isBlank(title) || isBlank(issuer))
            return fail(ra, "/credentials", "Employee, title and issuer are required.");
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate))
            return fail(ra, "/credentials", "Expiry date cannot be before the issue date.");
        if (durationHours != null && (durationHours < 0 || durationHours > 100000))
            return fail(ra, "/credentials", "Duration must be a sensible number of hours.");
        if (!isBlank(credentialUrl) && !credentialUrl.trim().toLowerCase().matches("https?://.+"))
            return fail(ra, "/credentials", "Verification URL must start with http:// or https://.");

        var c = new EmployeeCredential();
        c.setEmployee(employee);
        c.setType(type);
        c.setTitle(title.trim());
        c.setIssuer(issuer.trim());
        c.setCredentialNumber(blankToNull(credentialNumber));
        c.setCredentialUrl(blankToNull(credentialUrl));
        if (type == CredentialType.ACADEMIC_DEGREE || type == CredentialType.ACADEMIC_CERTIFICATE) {
            c.setEducationLevel(educationLevel);
            c.setFieldOfStudy(blankToNull(fieldOfStudy));
            c.setGrade(blankToNull(grade));
        }
        c.setDurationHours(durationHours);
        c.setIssueDate(issueDate);
        c.setExpiryDate(expiryDate);
        if (skillId != null) skills.findById(skillId).ifPresent(c::setSkill);

        try {
            if (document != null && !document.isEmpty()) {
                c.setDocumentFileName(storage.saveDocument(document));
                c.setDocumentOriginalName(document.getOriginalFilename());
                c.setDocumentContentType(document.getContentType());
                c.setDocumentSize(document.getSize());
            }
        } catch (IllegalArgumentException | IOException ex) {
            return fail(ra, "/credentials", ex.getMessage());
        }
        credentials.save(c);
        logger.log(req, "CREATE", "EmployeeCredential", c.getId(), "Added " + type + " '" + c.getTitle() + "' for " + employee.getEmployeeCode());
        return ok(ra, "/credentials", "Credential added and awaiting verification.");
    }

    @PostMapping("/credentials/{id}/verify")
    public String verify(@PathVariable Long id,
                         @RequestParam VerificationStatus status,
                         @RequestParam(required = false) String notes,
                         HttpServletRequest req, RedirectAttributes ra) {
        var c = credentials.findWithEmployeeById(id).orElse(null);
        var me = currentUser.employee().orElse(null);
        if (c == null || me == null) return fail(ra, "/credentials", "Credential or your employee record not found.");
        if (status == VerificationStatus.PENDING) return fail(ra, "/credentials", "Invalid decision.");
        if (c.getEmployee().getId().equals(me.getId()))
            return fail(ra, "/credentials", "You cannot verify your own credential (maker-checker rule).");
        if (status == VerificationStatus.REJECTED && isBlank(notes))
            return fail(ra, "/credentials", "A note is required when rejecting.");

        c.setVerificationStatus(status);
        c.setVerifiedBy(me);
        c.setVerifiedOn(LocalDate.now());
        c.setVerificationNotes(blankToNull(notes));
        credentials.save(c);
        logger.log(req, status == VerificationStatus.VERIFIED ? "APPROVE" : "REJECT", "EmployeeCredential", id, c.getTitle() + " -> " + status);
        return ok(ra, "/credentials", "Verification recorded.");
    }

    /** The uploaded evidence: the owner and the staff roles may open it; other employees may not. */
    @GetMapping("/credentials/{id}/document")
    public ResponseEntity<Resource> credentialDocument(@PathVariable Long id, HttpServletRequest req) throws IOException {
        var c = credentials.findWithEmployeeById(id).orElse(null);
        if (c == null || c.getDocumentFileName() == null) return ResponseEntity.notFound().build();
        var me = currentUser.employee().orElse(null);
        boolean owner = me != null && c.getEmployee().getId().equals(me.getId());
        if (currentUser.isRestricted() && !owner) {
            logger.log(req, "ACCESS_DENIED", "EmployeeCredential", id, "Attempt to open another employee's document");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Resource r = storage.load(c.getDocumentFileName());
        if (!r.exists()) return ResponseEntity.notFound().build();
        String stored = c.getDocumentFileName();
        String downloadName = "credential-" + id + stored.substring(stored.lastIndexOf('.'));
        logger.log(req, "VIEW_DOCUMENT", "EmployeeCredential", id, "Opened document of " + c.getEmployee().getEmployeeCode());
        return ResponseEntity.ok()
                .contentType(FileStorageService.mediaTypeFor(stored))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(downloadName, StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(r);
    }



}
