package com.osm.production.client;

 import com.xdev.communicator.models.shared.OSMUserDTO;
 import org.springframework.cloud.openfeign.FeignClient;
 import org.springframework.http.ResponseEntity;
 import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "osm-sec", url = "${security.service.url:http://localhost:8088}")// ou url via configuration
public interface SecurityClient {

    @GetMapping("/api/security/user/role/{roleName}")
    ResponseEntity<List<OSMUserDTO>> getUsersByRole(@PathVariable("roleName") String roleName);
}