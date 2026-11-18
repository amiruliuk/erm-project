package com.enterprise.erm.web;

import com.enterprise.erm.service.AuditService;
import com.enterprise.erm.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Writes an audit entry for the signed-in user; auditing must never break the action itself. */
@Component
@RequiredArgsConstructor
public class ActionLogger {

    private final AuditService audit;
    private final CurrentUserService currentUser;

    public void log(HttpServletRequest req, String action, String type, Long id, String details) {
        try {
            audit.record(currentUser.username(), action, type, id, details, req.getRemoteAddr());
        } catch (Exception ignored) {
            // intentionally ignored
        }
    }
}
