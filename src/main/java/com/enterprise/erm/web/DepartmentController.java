package com.enterprise.erm.web;

import com.enterprise.erm.domain.Department;
import com.enterprise.erm.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final com.enterprise.erm.service.UnitService unitService;

    /** never let a form post overwrite the key or audit columns */
    @InitBinder("department")
    void restrictBinding(org.springframework.web.bind.WebDataBinder binder) {
        binder.setDisallowedFields("id", "createdAt", "updatedAt");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        model.addAttribute("unitCounts", unitService.unitCountsByDepartment());
        return "departments/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("department", new Department());
        model.addAttribute("pageTitle", "New Department");
        return "departments/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("department") Department department,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "New Department");
            return "departments/form";
        }
        try {
            departmentService.create(department);
            redirectAttributes.addFlashAttribute("flashSuccess", "Department created successfully");
            return "redirect:/departments";
        } catch (IllegalArgumentException ex) {
            result.rejectValue(ex.getMessage().startsWith("Department code") ? "code" : "name", "duplicate", ex.getMessage());
            model.addAttribute("pageTitle", "New Department");
            return "departments/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("department", departmentService.findById(id));
        model.addAttribute("pageTitle", "Edit Department");
        return "departments/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("department") Department department,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Edit Department");
            return "departments/form";
        }
        try {
            departmentService.update(id, department);
            redirectAttributes.addFlashAttribute("flashSuccess", "Department updated successfully");
            return "redirect:/departments";
        } catch (IllegalArgumentException ex) {
            result.rejectValue(ex.getMessage().startsWith("Department code") ? "code" : "name", "duplicate", ex.getMessage());
            model.addAttribute("pageTitle", "Edit Department");
            return "departments/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("flashSuccess", "Department deleted successfully");
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("flashError",
                    "This department is still used by employees or projects. Mark it inactive instead of deleting it.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("flashError", ex.getMessage());
        }
        return "redirect:/departments";
    }
}