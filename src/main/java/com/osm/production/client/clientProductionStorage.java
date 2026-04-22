package com.osm.production.client;

import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.StorageUnitDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "production-service", contextId = "labelStorageClient", url = "${production.service.url}")
public interface clientProductionStorage {

    @GetMapping("/api/production/storage-units/fetch/{id}")
    ApiResponse<StorageUnitDto> getStorageUnit(@PathVariable("id") UUID id);
}
