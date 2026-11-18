package com.enterprise.erm.config;

import com.enterprise.erm.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Records every state-changing request (POST/PUT/DELETE...) with user, URL, result and IP. */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private final AuditService audit;

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String m = req.getMethod();
        if ("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m)) return;
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            audit.record(a == null ? "anonymous" : a.getName(), m, "HTTP", null,
                    req.getRequestURI() + " -> HTTP " + res.getStatus(), req.getRemoteAddr());
        } catch (Exception ignored) {
            // auditing must never break the request
        }
    }
}
