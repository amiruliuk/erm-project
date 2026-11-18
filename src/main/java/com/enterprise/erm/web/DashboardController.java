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

/** Overview page and login page. */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final WorkItemRepository workItems;
    private final ApprovalRepository approvals;
    private final ResourceAllocationRepository resourceAllocations;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- dashboard / login

    @GetMapping("/")
    public String dashboard(Model m) {
        m.addAttribute("employeeCount", employees.count());
        m.addAttribute("projectCount", projects.count());
        m.addAttribute("openWork", workItems.countByStatusNot("DONE"));
        m.addAttribute("pendingApprovals", approvals.countByStatus("PENDING"));
        m.addAttribute("recentProjects", projects.findAll());
        m.addAttribute("allocationCount", resourceAllocations.count());
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }


}
