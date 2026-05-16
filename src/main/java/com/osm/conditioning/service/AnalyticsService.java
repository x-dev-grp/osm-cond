package com.osm.conditioning.service;

import com.osm.conditioning.Enum.ResultStatus;
import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionAnalytics;
import com.osm.conditioning.dto.BOMDto;
import com.osm.conditioning.dto.BomLineDto;
import com.osm.conditioning.dto.analytics.*;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.model.QCResult;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.osm.conditioning.repository.QCResultRepository;
import com.xdev.xdevbase.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalyticsService {



    private final OrdreFabricationRepository ofRepository;
    private final QCResultRepository qcResultRepository;
    private final clientInventaire inventaireClient;
    private final clientProductionAnalytics productionAnalyticsClient;

    // ─────────────────────────────────────────────
    // 23.1 Rendements des OF
    // ─────────────────────────────────────────────
    public List<OfYieldDto> getOfYieldsReport(ReportRequestDto request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Fetching yields report for tenant: {}, request: {}", tenantId, request);

        List<OfAnalyticsProjection> results = ofRepository.findByTenantIdAndIsDeletedFalse(tenantId);
        log.debug("Found {} OFs for analytics", results.size());

        return results.stream()
                .filter(of -> of.getQuantiteCible() != null && of.getQuantiteCible().compareTo(BigDecimal.ZERO) > 0)
                .filter(of -> {
                    if (request.getStartDate() == null && request.getEndDate() == null) return true;
                    if (of.getDateDebutPrevue() == null) return false;
                    boolean afterStart = request.getStartDate() == null || !of.getDateDebutPrevue().isBefore(request.getStartDate());
                    boolean beforeEnd = request.getEndDate() == null || !of.getDateDebutPrevue().isAfter(request.getEndDate());
                    return afterStart && beforeEnd;
                })
                .map(this::mapToYieldDtoFromProjection)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // 23.2 Rapport Global des OF
    // ─────────────────────────────────────────────
    public GlobalOfReportDto getGlobalOfReport(ReportRequestDto request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.debug("Fetching global report for tenant: {}", tenantId);
        
        List<OfAnalyticsProjection> all = ofRepository.findByTenantIdAndIsDeletedFalse(tenantId);
        List<OfAnalyticsProjection> filtered = all.stream()
                .filter(of -> {
                    if (request.getStartDate() == null && request.getEndDate() == null) return true;
                    if (of.getDateDebutPrevue() == null) return false;
                    boolean afterStart = request.getStartDate() == null || !of.getDateDebutPrevue().isBefore(request.getStartDate());
                    boolean beforeEnd = request.getEndDate() == null || !of.getDateDebutPrevue().isAfter(request.getEndDate());
                    return afterStart && beforeEnd;
                })
                .collect(Collectors.toList());
        
        log.debug("Filtered {} OFs from total {}", filtered.size(), all.size());

        GlobalOfReportDto dto = new GlobalOfReportDto();
        dto.setTotalOf(filtered.size());
        dto.setPlannedOf(filtered.stream().filter(of -> of.getStatut() == StatutOF.PLANIFIE).count());
        dto.setInProgressOf(filtered.stream().filter(of -> of.getStatut() == StatutOF.EN_COURS).count());
        dto.setCompletedOf(filtered.stream().filter(of -> of.getStatut() == StatutOF.TERMINE).count());
        dto.setCanceledOf(filtered.stream().filter(of -> of.getStatut() == StatutOF.CLOTURE).count());

        BigDecimal totalTarget = filtered.stream()
                .map(of -> of.getQuantiteCible() != null ? of.getQuantiteCible() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProduced = filtered.stream()
                .map(of -> of.getQuantiteBonne() != null ? of.getQuantiteBonne() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setTotalTargetQuantity(totalTarget);
        dto.setTotalProducedQuantity(totalProduced);
        return dto;
    }

    // ─────────────────────────────────────────────
    // 23.3 Taux de Non-conformité (QCResult de l'OF)
    // ─────────────────────────────────────────────
    public List<QualityReportDto> getQualityReport(ReportRequestDto request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<OfAnalyticsProjection> ofs = ofRepository.findByTenantIdAndIsDeletedFalse(tenantId).stream()
                .filter(of -> {
                    if (request.getStartDate() == null && request.getEndDate() == null) return true;
                    if (of.getDateDebutPrevue() == null) return false;
                    boolean afterStart = request.getStartDate() == null || !of.getDateDebutPrevue().isBefore(request.getStartDate());
                    boolean beforeEnd = request.getEndDate() == null || !of.getDateDebutPrevue().isAfter(request.getEndDate());
                    return afterStart && beforeEnd;
                })
                .collect(Collectors.toList());

        // Récupérer tous les résultats QC liés aux OFs filtrés
        List<QCResult> allResults = ofs.stream()
                .flatMap(of -> {
                    try {
                        return qcResultRepository.findByOfIdAndTenantIdOrderByDateControleDesc(of.getId(), tenantId).stream();
                    } catch (Exception e) {
                        log.warn("Impossible de récupérer les QCResults pour OF {}: {}", of.getId(), e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        if (allResults.isEmpty()) {
            return Collections.emptyList();
        }

        // Grouper par nom du point de contrôle
        Map<String, List<QCResult>> byControlPoint = allResults.stream()
                .collect(Collectors.groupingBy(r ->
                        (r.getControlPoint() != null && r.getControlPoint().getNom() != null)
                                ? r.getControlPoint().getNom()
                                : "Point de contrôle inconnu"));

        return byControlPoint.entrySet().stream()
                .map(entry -> {
                    List<QCResult> results = entry.getValue();
                    long nokCount = results.stream().filter(r -> r.getStatut() == ResultStatus.NOK).count();
                    BigDecimal ncRate = results.isEmpty() ? BigDecimal.ZERO
                            : BigDecimal.valueOf(nokCount * 100.0 / results.size()).setScale(2, RoundingMode.HALF_UP);

                    QualityReportDto dto = new QualityReportDto();
                    dto.setProductName(entry.getKey());
                    dto.setTotalControls(results.size());
                    dto.setFailedControls(nokCount);
                    dto.setNonConformityRate(ncRate);
                    return dto;
                })
                .sorted(Comparator.comparing(QualityReportDto::getNonConformityRate).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // 23.4 Écarts de Consommation BOM
    // ─────────────────────────────────────────────
    public List<BomGapDto> getBomGapReport(ReportRequestDto request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<OfAnalyticsProjection> ofs = ofRepository.findByTenantIdAndIsDeletedFalse(tenantId).stream()
                .filter(of -> of.getBomId() != null)
                .filter(of -> {
                    if (request.getStartDate() == null && request.getEndDate() == null) return true;
                    if (of.getDateDebutPrevue() == null) return false;
                    boolean afterStart = request.getStartDate() == null || !of.getDateDebutPrevue().isBefore(request.getStartDate());
                    boolean beforeEnd = request.getEndDate() == null || !of.getDateDebutPrevue().isAfter(request.getEndDate());
                    return afterStart && beforeEnd;
                })
                .collect(Collectors.toList());

        List<BomGapDto> result = new ArrayList<>();

        for (OfAnalyticsProjection of : ofs) {
            try {
                BOMDto bom = inventaireClient.getBomById(of.getBomId());
                if (bom == null || bom.getLines() == null || bom.getLines().isEmpty()) continue;

                BigDecimal quantiteCible = of.getQuantiteCible() != null ? of.getQuantiteCible() : BigDecimal.ZERO;
                BigDecimal quantiteBonne = of.getQuantiteBonne() != null ? of.getQuantiteBonne() : BigDecimal.ZERO;

                for (BomLineDto line : bom.getLines()) {
                    // quantity is a primitive double, it cannot be null

                    // Consommation théorique (planifiée) = quantité BOM par unité × quantité cible OF
                    BigDecimal planned = BigDecimal.valueOf(line.getQuantity()).multiply(quantiteCible).setScale(3, RoundingMode.HALF_UP);
                    // Consommation estimée réelle = quantité BOM par unité × quantité bonne produite
                    BigDecimal actual = BigDecimal.valueOf(line.getQuantity()).multiply(quantiteBonne).setScale(3, RoundingMode.HALF_UP);
                    BigDecimal gap = planned.subtract(actual);
                    BigDecimal gapPct = planned.compareTo(BigDecimal.ZERO) > 0
                            ? gap.multiply(BigDecimal.valueOf(100)).divide(planned, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    BomGapDto dto = new BomGapDto();
                    dto.setMaterialName("Article " + line.getArticleId().toString().substring(0, 8) + " — OF: " + of.getCode());
                    dto.setPlannedQuantity(planned);
                    dto.setActualQuantity(actual);
                    dto.setGapQuantity(gap);
                    dto.setGapPercentage(gapPct);
                    result.add(dto);
                }
            } catch (Exception e) {
                log.warn("Impossible de récupérer le BOM {} pour OF {}: {}", of.getBomId(), of.getCode(), e.getMessage());
            }
        }

        // Trier par écart décroissant pour mettre en avant les problèmes
        result.sort(Comparator.comparing(BomGapDto::getGapQuantity).reversed());
        return result;
    }

    // ─────────────────────────────────────────────
    // 23.5 Efficacité Filtrage (via Feign → osm-prod)
    // ─────────────────────────────────────────────
    public List<FiltrationReportDto> getFiltrationReport(ReportRequestDto request) {
        try {
            return productionAnalyticsClient.getFiltrationReport(request.getStartDate(), request.getEndDate());
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des données de filtrage depuis osm-prod: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────
    private OfYieldDto mapToYieldDtoFromProjection(OfAnalyticsProjection of) {
        OfYieldDto dto = new OfYieldDto();
        dto.setOfCode(of.getCode());
        dto.setStatut(of.getStatut() != null ? of.getStatut().name() : "INCONNU");
        dto.setSkuId(of.getProductId());
        dto.setQuantiteCible(of.getQuantiteCible());
        dto.setQuantiteBonne(of.getQuantiteBonne() != null ? of.getQuantiteBonne() : BigDecimal.ZERO);
        if (dto.getQuantiteCible() != null && dto.getQuantiteCible().compareTo(BigDecimal.ZERO) > 0) {
            dto.setYieldPercentage(dto.getQuantiteBonne()
                    .multiply(new BigDecimal(100))
                    .divide(dto.getQuantiteCible(), 2, RoundingMode.HALF_UP));
        } else {
            dto.setYieldPercentage(BigDecimal.ZERO);
        }
        return dto;
    }

    private OfYieldDto mapToYieldDto(OrdreFabrication of) {
        OfYieldDto dto = new OfYieldDto();
        dto.setOfCode(of.getCode());
        dto.setStatut(of.getStatut() != null ? of.getStatut().name() : "INCONNU");
        dto.setSkuId(of.getProductId());
        dto.setQuantiteCible(of.getQuantiteCible());
        dto.setQuantiteBonne(of.getQuantiteBonne() != null ? of.getQuantiteBonne() : BigDecimal.ZERO);
        if (dto.getQuantiteCible() != null && dto.getQuantiteCible().compareTo(BigDecimal.ZERO) > 0) {
            dto.setYieldPercentage(dto.getQuantiteBonne()
                    .multiply(new BigDecimal(100))
                    .divide(dto.getQuantiteCible(), 2, RoundingMode.HALF_UP));
        } else {
            dto.setYieldPercentage(BigDecimal.ZERO);
        }
        return dto;
    }
}
