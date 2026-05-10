package com.osm.conditioning.client;

import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.OilTransactionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "oilproductionservice", contextId = "labelOilTransactionClient", url = "${production.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientProductionOilTransaction {

    @GetMapping("/api/production/oil_transaction/storage-unit/{storageUnitId}")
    ApiResponse<List<OilTransactionDTO>> getByStorageUnit(@PathVariable("storageUnitId") UUID storageUnitId);
}
