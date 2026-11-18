package com.enterprise.erm.web;

import com.enterprise.erm.domain.Evidence;
import com.enterprise.erm.repository.EvidenceRepository;
import com.enterprise.erm.service.AuditService;
import com.enterprise.erm.service.EvidenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

/** Serves evidence files. URL rule in SecurityConfig: ADMIN / MANAGER / APPROVER / AUDITOR only. Every download is audited. */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','APPROVER','AUDITOR')")
public class EvidenceController {

    private final EvidenceRepository evidenceRepo;
    private final EvidenceService evidenceService;
    private final AuditService audit;

    @GetMapping("/evidence/{id}/download")
    public void download(@PathVariable Long id, Authentication auth,
                         HttpServletRequest req, HttpServletResponse res) throws IOException {
        Evidence e = evidenceRepo.findById(id).orElse(null);
        if (e == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        audit.record(auth.getName(), "VIEW_DOCUMENT", "Evidence", id,
                "Downloaded evidence '" + e.getTitle() + "'", req.getRemoteAddr());
        evidenceService.writeTo(e, res);
    }
}
