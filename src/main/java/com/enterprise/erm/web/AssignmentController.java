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

/** Resource recommendations (managers only). */
@Controller
@RequiredArgsConstructor
public class AssignmentController {

    private final SkillRepository skills;
    private final UnitRepository units;
    private final UnitService unitService;
    private final AssignmentRecommendationService recommendations;
    private final ActionLogger logger;

    // ---------------------------------------------------------------- recommendations (managers only)

    @GetMapping("/assignments/recommend")
    public String recommend(@RequestParam(required = false) Long skillId,
                            @RequestParam(defaultValue = "3") int requiredLevel,
                            @RequestParam(required = false) Long unitId,
                            Model m) {
        m.addAttribute("units", unitService.findAll());
        m.addAttribute("unitId", unitId);
        List<Skill> allSkills = skills.findAll();
        if (skillId == null && !allSkills.isEmpty()) skillId = allSkills.get(0).getId();
        m.addAttribute("recommendations",
                skillId == null ? List.of() : recommendations.recommend(skillId, requiredLevel, unitId));
        m.addAttribute("skills", allSkills);
        m.addAttribute("skillId", skillId);
        m.addAttribute("requiredLevel", requiredLevel);
        return "recommendations";
    }


}
