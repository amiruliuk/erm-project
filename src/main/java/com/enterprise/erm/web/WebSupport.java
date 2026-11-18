package com.enterprise.erm.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

/** Small helpers shared by the web controllers. */
final class WebSupport {

    static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private WebSupport() { }

    static boolean isBlank(String s) { return s == null || s.isBlank(); }

    static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }

    static String ok(RedirectAttributes ra, String path, String msg) {
        ra.addFlashAttribute("flashSuccess", msg);
        return "redirect:" + path;
    }

    static String fail(RedirectAttributes ra, String path, String msg) {
        ra.addFlashAttribute("flashError", msg);
        return "redirect:" + path;
    }
}
