package com.osm.conditioning.client;

import com.xdev.communicator.models.shared.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "oilproductionservice", contextId = "labelFiltrationClient", url = "${production.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientProductionFiltration {

    @GetMapping("/api/production/filtration/{id}")
    ApiResponse<Object> getFiltration(@PathVariable("id") UUID id);
}
