package com.enterprise.erm.web;

import com.enterprise.erm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository logs;

    /** Read-only. Restricted to ADMIN and AUDITOR in SecurityConfig. */
    @GetMapping("/audit")
    public String audit(Model m) {
        m.addAttribute("logs", logs.findTop200ByOrderByCreatedAtDesc());
        return "audit";
    }

    @GetMapping("/denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied() {
        return "denied";
    }
}
