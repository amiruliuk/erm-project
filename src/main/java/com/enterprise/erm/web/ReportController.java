package com.enterprise.erm.web;

import com.enterprise.erm.service.ReportService;
import com.enterprise.erm.service.TeamReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reports;
    private final TeamReportService teamReports;

    @GetMapping
    public String index(Model m) {
        m.addAllAttributes(reports.summary());
        return "reports";
    }

    @GetMapping("/{type}")
    public String view(@PathVariable String type, @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long team,
                       @RequestParam(required = false) Long unit, Model m) {
        var report = build(type, status, team, unit);
        if (report == null) return "redirect:/reports";
        if (TeamReportService.isTeamReport(type)) {      // team reports get a Team filter
            m.addAttribute("teams", teamReports.teams());
            m.addAttribute("team", team);
            m.addAttribute("unitOptions", teamReports.unitList());
            m.addAttribute("unit", unit);
        }
        m.addAttribute("report", report);
        m.addAttribute("type", type);
        m.addAttribute("status", status == null ? "" : status);
        m.addAttribute("statuses", reports.statusesFor(type));
        return "report-view";
    }

    @GetMapping("/{type}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String type, @RequestParam(required = false) String status,
                                      @RequestParam(required = false) Long team,
                                      @RequestParam(required = false) Long unit) {
        var report = build(type, status, team, unit);
        if (report == null) return ResponseEntity.notFound().build();

        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM so Excel reads UTF-8
        sb.append(line(report.headers()));
        report.rows().forEach(r -> sb.append(line(r)));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + type + "-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ReportService.Report build(String type, String status, Long team, Long unit) {
        return TeamReportService.isTeamReport(type) ? teamReports.build(type, team, unit) : reports.build(type, status);
    }

    private static String line(List<String> cells) {
        return cells.stream().map(ReportController::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    /** Quotes the value and neutralises spreadsheet formula injection (= + - @ at the start). */
    private static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
