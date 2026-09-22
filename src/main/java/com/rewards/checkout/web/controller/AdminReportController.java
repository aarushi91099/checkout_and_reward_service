package com.rewards.checkout.web.controller;

import com.rewards.checkout.service.ReportService;
import com.rewards.checkout.web.dto.ReportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<ReportResponse> getReport() {
        return ResponseEntity.ok(reportService.getReport());
    }
}
