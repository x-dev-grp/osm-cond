package com.osm.conditioning.client;

import com.xdev.communicator.models.shared.CompanyProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "security-service", contextId = "labelCompanyProfileClient", url = "${security.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientSecurityCompanyProfile {

    @GetMapping("/api/security/company-profile/by-tenant/{tenantId}")
    CompanyProfileDto getByTenantId(@PathVariable("tenantId") UUID tenantId);
}
