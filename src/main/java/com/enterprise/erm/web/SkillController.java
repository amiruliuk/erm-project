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

/** Skill catalogue and assessments (employees see their own). */
@Controller
@RequiredArgsConstructor
public class SkillController {

    private final EmployeeRepository employees;
    private final SkillRepository skills;
    private final EmployeeSkillRepository employeeSkills;
    private final CurrentUserService currentUser;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- skills (employees see their own)

    @GetMapping("/skills")
    public String skills(Model m) {
        m.addAttribute("skills", skills.findAll());
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("employeeSkills", currentUser.isRestricted()
                ? currentUser.employee().map(e -> employeeSkills.findByEmployeeId(e.getId())).orElse(List.of())
                : employeeSkills.findAll());
        return "skills";
    }

    @PostMapping("/skills")
    public String saveSkill(@RequestParam String name,
                            @RequestParam(required = false) String category,
                            HttpServletRequest req, RedirectAttributes ra) {
        if (isBlank(name)) return fail(ra, "/skills", "Skill name is required.");
        var s = new Skill();
        s.setName(name.trim());
        s.setCategory(blankToNull(category));
        try {
            skills.save(s);
        } catch (DataIntegrityViolationException ex) {
            return fail(ra, "/skills", "That skill already exists.");
        }
        logger.log(req, "CREATE", "Skill", s.getId(), "Added skill " + s.getName());
        return ok(ra, "/skills", "Skill added.");
    }

    @PostMapping("/skills/assess")
    public String assess(@RequestParam Long employeeId,
                         @RequestParam Long skillId,
                         @RequestParam int proficiency,
                         HttpServletRequest req, RedirectAttributes ra) {
        var employee = employees.findById(employeeId).orElse(null);
        var skill = skills.findById(skillId).orElse(null);
        if (employee == null || skill == null) {
            return fail(ra, "/skills", "Selected employee or skill no longer exists.");
        }
        // one assessment per employee+skill (unique constraint) -> update instead of insert
        var es = employeeSkills.findByEmployeeIdAndSkillId(employeeId, skillId).orElseGet(EmployeeSkill::new);
        es.setEmployee(employee);
        es.setSkill(skill);
        es.setProficiency(Math.max(1, Math.min(5, proficiency)));
        es.setAssessedOn(LocalDate.now());
        employeeSkills.save(es);
        logger.log(req, "ASSESS", "EmployeeSkill", es.getId(), employee.getEmployeeCode() + " / " + skill.getName() + " = " + es.getProficiency());
        return ok(ra, "/skills", "Assessment saved.");
    }


}
