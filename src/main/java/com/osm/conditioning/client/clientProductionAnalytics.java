package com.osm.conditioning.client;

import com.osm.conditioning.dto.analytics.FiltrationReportDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign Client pour consommer les endpoints analytiques de osm-prod.
 * Utilise le même contextId pattern que les autres clients du module.
 */
@FeignClient(name = "oilproductionservice", contextId = "analyticsClient", url = "${production.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientProductionAnalytics {

    // 23.5 Filtrage : récupère les opérations de filtrage depuis osm-prod
    @GetMapping("/api/production/analytics/filtration")

    List<FiltrationReportDto> getFiltrationReport(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate);
}
