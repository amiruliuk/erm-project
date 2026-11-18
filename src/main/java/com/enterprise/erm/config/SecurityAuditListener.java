package com.enterprise.erm.config;

import com.enterprise.erm.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Writes sign-in successes and failures to the audit log. */
@Component
@RequiredArgsConstructor
public class SecurityAuditListener {

    private final AuditService audit;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent e) {
        record(e.getAuthentication(), "LOGIN_SUCCESS", "Successful sign-in");
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent e) {
        record(e.getAuthentication(), "LOGIN_FAILURE",
                "Failed sign-in: " + e.getException().getClass().getSimpleName());
    }

    private void record(Authentication a, String action, String details) {
        try {
            String ip = a.getDetails() instanceof WebAuthenticationDetails d ? d.getRemoteAddress() : null;
            audit.record(String.valueOf(a.getName()), action, "AppUser", null, details, ip);
        } catch (Exception ignored) {
            // auditing must never break authentication
        }
    }
}
