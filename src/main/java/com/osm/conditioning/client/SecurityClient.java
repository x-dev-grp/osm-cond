package com.osm.conditioning.client;

import com.osm.conditioning.dto.AssignableUserDTO;
import com.xdev.communicator.models.shared.OSMUserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "osm-sec", url = "${security.service.url:http://localhost:8088}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)// ou url via configuration
public interface SecurityClient {

    @GetMapping("/api/security/user/role/{roleName}")
    ResponseEntity<List<OSMUserDTO>> getUsersByRole(@PathVariable("roleName") String roleName);

    @GetMapping("/api/security/user/assignable")
    ResponseEntity<List<AssignableUserDTO>> getUsersByPermission(@RequestParam("module") String module,
                                                                 @RequestParam("entity") String entity,
                                                                 @RequestParam("permission") String permission);
}
