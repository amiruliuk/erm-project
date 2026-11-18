package com.enterprise.erm.domain.dto;


import java.util.List;

public record SkillMatrixReport(
        List<SkillMatrixGroup> groups,       // <- was List<String> employeeColumns
        List<SkillMatrixRow> rows) {}