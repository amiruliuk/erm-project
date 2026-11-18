package com.enterprise.erm.service;

import com.enterprise.erm.domain.*;
import com.enterprise.erm.repository.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceDocxExportService {

    private final GovernanceAuditService governanceAuditService;
    private final EmployeeRepository employeeRepository;
    private final SkillRepository skillRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final BackupRosterRepository backupRosterRepository;
    private final CrossTrainingPlanRepository crossTrainingPlanRepository;

    public GovernanceDocxExportService(
            GovernanceAuditService governanceAuditService,
            EmployeeRepository employeeRepository,
            SkillRepository skillRepository,
            EmployeeSkillRepository employeeSkillRepository,
            BackupRosterRepository backupRosterRepository,
            CrossTrainingPlanRepository crossTrainingPlanRepository) {
        this.governanceAuditService = governanceAuditService;
        this.employeeRepository = employeeRepository;
        this.skillRepository = skillRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.backupRosterRepository = backupRosterRepository;
        this.crossTrainingPlanRepository = crossTrainingPlanRepository;
    }

    /**
     * Generates CBS-GOV-003: Skill Matrix and Backup Roster (.docx)
     */
    public byte[] generateCbsGov003Docx(String ownerName, String approvedBy) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Document Header Block
            addTitle(doc, "Skill Matrix and Backup Roster");
            addMetaLine(doc, "Document ID: CBS-GOV-003 | Version: 1.0 | Owner: " + ownerName
                    + " | Review: Every 6 months or after a team change | Generated: "
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")));

            // 2. Proficiency Scale Table
            addSectionHeading(doc, "1. Proficiency Scale");
            XWPFTable scaleTable = doc.createTable(6, 2);
            styleHeaderRow(scaleTable.getRow(0), "1B365D", "Level", "Meaning");
            String[][] levels = {
                    {"0", "No knowledge"},
                    {"1", "Basic awareness; needs full supervision"},
                    {"2", "Can perform with guidance"},
                    {"3", "Can perform independently"},
                    {"4", "Expert; can train and cover others"}
            };
            for (int i = 0; i < levels.length; i++) {
                setRowCells(scaleTable.getRow(i + 1), levels[i][0], levels[i][1]);
            }

            // 3. Skill Matrix & Single-Person Risk Analysis
            addSectionHeading(doc, "2. CBS Skill Matrix & Single-Person Risk Assessment");
            List<Map<String, Object>> domainRisks = governanceAuditService.evaluateSkillDomainRisks();
            XWPFTable riskTable = doc.createTable(domainRisks.size() + 1, 5);
            styleHeaderRow(riskTable.getRow(0), "1B365D",
                    "Skill Domain", "Persons at Level 3+", "Single-Person Risk", "Qualified Staff (L3+)", "Audit Status");

            for (int i = 0; i < domainRisks.size(); i++) {
                Map<String, Object> r = domainRisks.get(i);
                boolean isRisk = Boolean.TRUE.equals(r.get("singlePersonRisk"));
                @SuppressWarnings("unchecked")
                List<String> staffList = (List<String>) r.getOrDefault("qualifiedStaff", List.of());
                setRowCells(riskTable.getRow(i + 1),
                        String.valueOf(r.get("skillName")),
                        String.valueOf(r.get("level3PlusCount")),
                        isRisk ? "YES (CRITICAL)" : "No",
                        staffList.isEmpty() ? "None" : String.join(", ", staffList),
                        String.valueOf(r.get("auditStatus")));
            }

            addNoteParagraph(doc, "Governance Rule: Any critical skill with fewer than 2 people at level 3 or above is a key-person dependency and requires an active mitigation action.");

            // 4. Backup and Succession Roster
            addSectionHeading(doc, "3. Backup and Succession Roster");
            List<Map<String, Object>> rosters = governanceAuditService.evaluateBackupRosterReadiness();
            XWPFTable rosterTable = doc.createTable(rosters.size() + 1, 5);
            styleHeaderRow(rosterTable.getRow(0), "1B365D",
                    "Critical Activity", "Primary", "Backup 1", "Backup 2", "Last Readiness Test");

            for (int i = 0; i < rosters.size(); i++) {
                BackupRoster br = (BackupRoster) rosters.get(i).get("roster");
                LocalDate lastTest = (LocalDate) rosters.get(i).get("lastTestDate");
                setRowCells(rosterTable.getRow(i + 1),
                        br.getActivity() != null ? br.getActivity().getName() : "-",
                        br.getPrimaryPerson() != null ? br.getPrimaryPerson().getFullName() : "-",
                        br.getBackup1() != null ? br.getBackup1().getFullName() : "-",
                        br.getBackup2() != null ? br.getBackup2().getFullName() : "-",
                        lastTest != null ? lastTest.toString() : "Pending Test");
            }

// 5. Mitigation & Cross-Training Plan
            addSectionHeading(doc, "4. Mitigation and Cross-Training Plan");
            List<CrossTrainingPlan> plans = crossTrainingPlanRepository.findAll();
            XWPFTable planTable = doc.createTable(plans.size() + 1, 5);
            styleHeaderRow(planTable.getRow(0), "1B365D",
                    "Dependency / Skill", "Mitigation Action", "Owner", "Target Date", "Status");

            for (int i = 0; i < plans.size(); i++) {
                CrossTrainingPlan p = plans.get(i);
                setRowCells(planTable.getRow(i + 1),
                        p.getDependency() != null ? p.getDependency().getName() : "General CBS",
                        p.getAction() != null ? p.getAction() : "-",
                        p.getOwner() != null ? p.getOwner().getFullName() : ownerName,
                        p.getTargetDate() != null ? p.getTargetDate().toString() : "TBD",
                        p.getStatus() != null ? p.getStatus().name() : "-");
            }
            // 6. Audit Sign-Off Block
            addSectionHeading(doc, "5. Governance Approval Sign-Off");
            XWPFTable signTable = doc.createTable(2, 3);
            styleHeaderRow(signTable.getRow(0), "1B365D", "Role", "Approved By", "Date");
            setRowCells(signTable.getRow(1), "IT Head / CIO", approvedBy, LocalDate.now().toString());

            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(16);
        r.setColor("1B365D");
    }

    private void addMetaLine(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(9);
        r.setColor("555555");
    }

    private void addSectionHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(12);
        r.setColor("0F294A");
    }

    private void addNoteParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(100);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setItalic(true);
        r.setFontSize(9);
    }

    private void styleHeaderRow(XWPFTableRow row, String hexBg, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            cell.setColor(hexBg);
            XWPFParagraph p = cell.getParagraphs().get(0);
            XWPFRun r = p.createRun();
            r.setText(headers[i]);
            r.setBold(true);
            r.setColor("FFFFFF");
            r.setFontSize(9);
        }
    }

    private void setRowCells(XWPFTableRow row, String... values) {
        for (int i = 0; i < values.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            XWPFParagraph p = cell.getParagraphs().get(0);
            XWPFRun r = p.createRun();
            r.setText(values[i] != null ? values[i] : "");
            r.setFontSize(9);
        }
    }
}