package com.osm.conditioning.client;

import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.StorageUnitDto;
import com.osm.conditioning.expedition.dto.GenealogyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "oilproductionservice", contextId = "labelStorageClient", url = "${production.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientProductionStorage {

    @GetMapping("/api/production/storage-units/fetch/{id}")
    ApiResponse<StorageUnitDto> getStorageUnit(@PathVariable("id") UUID id);

    @GetMapping("/api/production/traceability/genealogy/{id}")
    ApiResponse<GenealogyDto> getGenealogy(@PathVariable("id") UUID id);
}
