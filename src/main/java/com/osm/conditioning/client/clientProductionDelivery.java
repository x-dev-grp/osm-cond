package com.osm.conditioning.client;

import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.UnifiedDeliveryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "oilproductionservice", contextId = "labelDeliveryClient", url = "${production.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientProductionDelivery {

    @GetMapping("/api/production/deliveries/getDeliveryByLotNumber/{lotNumber}")
    ApiResponse<UnifiedDeliveryDTO> getDeliveryByLotNumber(@PathVariable("lotNumber") String lotNumber);
}
