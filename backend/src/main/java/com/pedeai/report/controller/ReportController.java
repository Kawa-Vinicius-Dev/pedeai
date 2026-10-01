package com.pedeai.report.controller;

import com.pedeai.report.dto.DashboardResponse;
import com.pedeai.report.dto.RevenueResponse;
import com.pedeai.report.service.ReportService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize(Permissions.VIEW_REPORTS)
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Sem {@code date}, o dia operacional de hoje. */
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(CurrentUser user, @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.dashboard(user.storeId(), date == null ? reportService.today(user.storeId()) : date);
    }

    @GetMapping("/revenue")
    public RevenueResponse revenue(CurrentUser user,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.revenue(user.storeId(), from, to);
    }
}
