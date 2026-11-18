package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GovernanceAuditService {

    private final SkillRepository skillRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final BackupRosterRepository backupRosterRepository;
    private final CrossTrainingPlanRepository crossTrainingPlanRepository;
    private final BackupReadinessTestRepository readinessTestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public GovernanceAuditService(
            SkillRepository skillRepository,
            EmployeeSkillRepository employeeSkillRepository,
            BackupRosterRepository backupRosterRepository,
            CrossTrainingPlanRepository crossTrainingPlanRepository,
            BackupReadinessTestRepository readinessTestRepository) {
        this.skillRepository = skillRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.backupRosterRepository = backupRosterRepository;
        this.crossTrainingPlanRepository = crossTrainingPlanRepository;
        this.readinessTestRepository = readinessTestRepository;
    }

    /**
     * CBS-GOV-003: Evaluates Single-Person Risk (< 2 staff at Proficiency Level 3+)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> evaluateSkillDomainRisks() {
        List<Skill> skills = skillRepository.findAll();
        List<EmployeeSkill> allEmpSkills = employeeSkillRepository.findAll();
        List<CrossTrainingPlan> activePlans = crossTrainingPlanRepository.findAll();

        List<Map<String, Object>> results = new ArrayList<>();

        for (Skill skill : skills) {
            List<EmployeeSkill> domainRatings = allEmpSkills.stream()
                    .filter(es -> es.getSkill() != null && es.getSkill().getId().equals(skill.getId()))
                    .toList();

            long level3PlusCount = domainRatings.stream()
                    .filter(es -> es.getProficiency() >= 3)   // primitive int — no null check
                    .count();

            boolean singlePersonRisk = level3PlusCount < 2;

            List<String> qualifiedStaff = domainRatings.stream()
                    .filter(es -> es.getProficiency() >= 3)
                    .map(es -> (es.getEmployee() != null ? es.getEmployee().getFullName() : "Unknown")
                            + " (L" + es.getProficiency() + ")")
                    .toList();

            boolean hasMitigationPlan = activePlans.stream()
                    .anyMatch(plan -> plan.getDependency() != null
                            && plan.getDependency().getId() != null
                            && plan.getDependency().getId().equals(skill.getId())
                            && plan.getStatus() != null
                            && plan.getStatus() != CrossTrainingPlan.PlanStatus.DONE);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillId", skill.getId());
            row.put("skillName", skill.getName());
            row.put("level3PlusCount", level3PlusCount);
            row.put("singlePersonRisk", singlePersonRisk);
            row.put("qualifiedStaff", qualifiedStaff);
            row.put("hasMitigationPlan", hasMitigationPlan);
            row.put("auditStatus", !singlePersonRisk ? "COMPLIANT"
                    : (hasMitigationPlan ? "MITIGATION_ACTIVE" : "UNMITIGATED_RISK"));
            results.add(row);
        }
        return results;
    }

    /**
     * CBS-GOV-003: Evaluates Backup & Succession Roster Readiness
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> evaluateBackupRosterReadiness() {
        List<BackupRoster> rosters = backupRosterRepository.findAll();
        List<BackupReadinessTest> tests = readinessTestRepository.findAll();

        List<Map<String, Object>> rosterReport = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (BackupRoster roster : rosters) {
            Optional<LocalDate> lastTestDate = tests.stream()
                    .filter(t -> t.getRoster() != null
                            && t.getRoster().getId().equals(roster.getId()))
                    .map(BackupReadinessTest::getTestedOn)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo);

            boolean overdue = lastTestDate
                    .map(d -> ChronoUnit.DAYS.between(d, today) > 180)
                    .orElse(true);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roster", roster);
            item.put("lastTestDate", lastTestDate.orElse(null));
            item.put("isOverdue", overdue);
            item.put("readinessStatus", overdue ? "TEST_DUE" : "VERIFIED");
            rosterReport.add(item);
        }
        return rosterReport;
    }

    /**
     * CBS-GOV-002: Detects Segregation of Duties (SoD) Conflicts
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectSodConflicts() {
        String sql = """
            SELECT a.staff_id, a.staff_name, a.system_name,
                   a.role_name AS role_1, b.role_name AS role_2,
                   a.exception_approved, a.compensating_control, a.exception_expiry
            FROM privileged_role_register a
            JOIN privileged_role_register b
              ON a.staff_id = b.staff_id
             AND a.system_name = b.system_name
             AND a.id < b.id
            JOIN sod_conflict_rule r
              ON ((r.role_a = a.role_name AND r.role_b = b.role_name)
               OR (r.role_a = b.role_name AND r.role_b = a.role_name))
            WHERE r.conflict_flag = TRUE
        """;

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> conflicts = new ArrayList<>();

        for (Object[] r : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("staffId", r[0]);
            map.put("staffName", r[1]);
            map.put("systemName", r[2]);
            map.put("role1", r[3]);
            map.put("role2", r[4]);
            boolean approved = r[5] != null && (Boolean) r[5];
            LocalDate expiry = toLocalDate(r[7]);
            boolean validException = approved && (expiry == null || !expiry.isBefore(LocalDate.now()));
            map.put("validException", validException);
            map.put("compensatingControl", r[6]);
            map.put("severity", validException ? "APPROVED_EXCEPTION" : "CRITICAL_BREACH");
            conflicts.add(map);
        }
        return conflicts;
    }

    /**
     * Null-safe conversion of a JDBC date/timestamp column to {@link LocalDate}.
     * Handles {@link java.sql.Date}, {@link java.sql.Timestamp}, and {@link LocalDate}.
     */
    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date type: " + value.getClass().getName());
    }

    /**
     * Executive Governance Summary for Dashboard
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExecutiveGovernanceSummary() {
        List<Map<String, Object>> skillRisks = evaluateSkillDomainRisks();
        long totalDomains = skillRisks.size();
        long singlePersonRisks = skillRisks.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("singlePersonRisk")))
                .count();
        long unmitigatedRisks = skillRisks.stream()
                .filter(m -> "UNMITIGATED_RISK".equals(m.get("auditStatus")))
                .count();

        List<Map<String, Object>> sodConflicts = detectSodConflicts();
        long activeSodBreaches = sodConflicts.stream()
                .filter(c -> "CRITICAL_BREACH".equals(c.get("severity")))
                .count();

        List<Map<String, Object>> rosterStatus = evaluateBackupRosterReadiness();
        long overdueDrills = rosterStatus.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("isOverdue")))
                .count();

        int complianceScore = 100;
        complianceScore -= (int) (activeSodBreaches * 15);
        complianceScore -= (int) (unmitigatedRisks * 5);
        complianceScore -= (int) (overdueDrills * 3);
        complianceScore = Math.max(0, complianceScore);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("complianceScore", complianceScore);
        summary.put("totalSkillDomains", totalDomains);
        summary.put("singlePersonRiskCount", singlePersonRisks);
        summary.put("unmitigatedRiskCount", unmitigatedRisks);
        summary.put("activeSodBreaches", activeSodBreaches);
        summary.put("overdueReadinessTests", overdueDrills);
        summary.put("skillRisks", skillRisks);
        summary.put("sodConflicts", sodConflicts);
        summary.put("rosterStatus", rosterStatus);
        return summary;
    }
}