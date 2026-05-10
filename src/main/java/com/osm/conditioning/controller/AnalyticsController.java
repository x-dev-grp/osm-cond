package com.osm.conditioning.controller;

import com.osm.conditioning.dto.analytics.BomGapDto;
import com.osm.conditioning.dto.analytics.FiltrationReportDto;
import com.osm.conditioning.dto.analytics.GlobalOfReportDto;
import com.osm.conditioning.dto.analytics.OfYieldDto;
import com.osm.conditioning.dto.analytics.QualityReportDto;
import com.osm.conditioning.dto.analytics.ReportRequestDto;
import com.osm.conditioning.service.AnalyticsService;
import com.osm.conditioning.service.PdfReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordreConditionement/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final PdfReportService pdfReportService;

    public AnalyticsController(AnalyticsService analyticsService, PdfReportService pdfReportService) {
        this.analyticsService = analyticsService;
        this.pdfReportService = pdfReportService;
    }

    // =====================================================
    // 1. Rendement OF
    // Front page: /analytics/of-yield
    // API JSON:   /api/ordreConditionement/analytics/of-yield
    // Legacy:     /api/ordreConditionement/analytics/reports/yields
    // =====================================================

    @PostMapping({"/of-yield", "/rendement-of", "/reports/yields"})
    public ResponseEntity<List<OfYieldDto>> getOfYieldsReport(@RequestBody(required = false) ReportRequestDto request) {
        return ResponseEntity.ok(
                analyticsService.getOfYieldsReport(normalizeRequest(request))
        );
    }

    @PostMapping({"/of-yield/pdf", "/rendement-of/pdf", "/reports/yields/pdf"})
    public ResponseEntity<byte[]> getOfYieldsReportPdf(@RequestBody(required = false) ReportRequestDto request) {
        ReportRequestDto safeRequest = normalizeRequest(request);
        List<OfYieldDto> data = analyticsService.getOfYieldsReport(safeRequest);

        byte[] pdfBytes = pdfReportService.generateOfYieldReportPdf(
                data,
                safeRequest.getStartDate(),
                safeRequest.getEndDate()
        );

        return pdfResponse(pdfBytes, "Rapport_Rendement_OF.pdf");
    }

    // =====================================================
    // 2. Rapport global OF
    // Front page: /analytics/global-of
    // API JSON:   /api/ordreConditionement/analytics/global-of
    // Legacy:     /api/ordreConditionement/analytics/reports/global
    // =====================================================

    @PostMapping({"/global-of", "/reports/global"})
    public ResponseEntity<GlobalOfReportDto> getGlobalOfReport(@RequestBody(required = false) ReportRequestDto request) {
        return ResponseEntity.ok(
                analyticsService.getGlobalOfReport(normalizeRequest(request))
        );
    }

    @PostMapping({"/global-of/pdf", "/reports/global/pdf"})
    public ResponseEntity<byte[]> getGlobalOfReportPdf(@RequestBody(required = false) ReportRequestDto request) {
        ReportRequestDto safeRequest = normalizeRequest(request);
        GlobalOfReportDto data = analyticsService.getGlobalOfReport(safeRequest);

        byte[] pdfBytes = pdfReportService.generateGlobalOfReportPdf(
                data,
                safeRequest.getStartDate(),
                safeRequest.getEndDate()
        );

        return pdfResponse(pdfBytes, "Rapport_Global_OF.pdf");
    }

    // =====================================================
    // 3. Rapport qualité
    // Front page: /analytics/quality ou /analytics/qualite
    // API JSON:   /api/ordreConditionement/analytics/quality
    // Legacy:     /api/ordreConditionement/analytics/reports/quality
    // =====================================================

    @PostMapping({"/quality", "/qualite", "/reports/quality"})
    public ResponseEntity<List<QualityReportDto>> getQualityReport(@RequestBody(required = false) ReportRequestDto request) {
        return ResponseEntity.ok(
                analyticsService.getQualityReport(normalizeRequest(request))
        );
    }

    @PostMapping({"/quality/pdf", "/qualite/pdf", "/reports/quality/pdf"})
    public ResponseEntity<byte[]> getQualityReportPdf(@RequestBody(required = false) ReportRequestDto request) {
        ReportRequestDto safeRequest = normalizeRequest(request);
        List<QualityReportDto> data = analyticsService.getQualityReport(safeRequest);

        byte[] pdfBytes = pdfReportService.generateQualityReportPdf(
                data,
                safeRequest.getStartDate(),
                safeRequest.getEndDate()
        );

        return pdfResponse(pdfBytes, "Rapport_Qualite.pdf");
    }

    // =====================================================
    // 4. Rapport BOM
    // Front page: /analytics/bom
    // API JSON:   /api/ordreConditionement/analytics/bom
    // Legacy:     /api/ordreConditionement/analytics/reports/bom-gap
    // =====================================================

    @PostMapping({"/bom", "/bom-gap", "/reports/bom-gap"})
    public ResponseEntity<List<BomGapDto>> getBomGapReport(@RequestBody(required = false) ReportRequestDto request) {
        return ResponseEntity.ok(
                analyticsService.getBomGapReport(normalizeRequest(request))
        );
    }

    @PostMapping({"/bom/pdf", "/bom-gap/pdf", "/reports/bom-gap/pdf"})
    public ResponseEntity<byte[]> getBomGapReportPdf(@RequestBody(required = false) ReportRequestDto request) {
        ReportRequestDto safeRequest = normalizeRequest(request);
        List<BomGapDto> data = analyticsService.getBomGapReport(safeRequest);

        byte[] pdfBytes = pdfReportService.generateBomGapReportPdf(
                data,
                safeRequest.getStartDate(),
                safeRequest.getEndDate()
        );

        return pdfResponse(pdfBytes, "Rapport_Ecarts_BOM.pdf");
    }

    // =====================================================
    // 5. Rapport filtration
    // Front page: /analytics/filtration
    // API JSON:   /api/ordreConditionement/analytics/filtration
    // Legacy:     /api/ordreConditionement/analytics/reports/filtration
    // =====================================================

    @PostMapping({"/filtration", "/reports/filtration"})
    public ResponseEntity<List<FiltrationReportDto>> getFiltrationReport(@RequestBody(required = false) ReportRequestDto request) {
        return ResponseEntity.ok(
                analyticsService.getFiltrationReport(normalizeRequest(request))
        );
    }

    @PostMapping({"/filtration/pdf", "/reports/filtration/pdf"})
    public ResponseEntity<byte[]> getFiltrationReportPdf(@RequestBody(required = false) ReportRequestDto request) {
        ReportRequestDto safeRequest = normalizeRequest(request);
        List<FiltrationReportDto> data = analyticsService.getFiltrationReport(safeRequest);

        byte[] pdfBytes = pdfReportService.generateFiltrationReportPdf(
                data,
                safeRequest.getStartDate(),
                safeRequest.getEndDate()
        );

        return pdfResponse(pdfBytes, "Rapport_Filtrage.pdf");
    }

    // =====================================================
    // Helpers
    // =====================================================

    private ReportRequestDto normalizeRequest(ReportRequestDto request) {
        return request != null ? request : new ReportRequestDto();
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdfBytes, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}