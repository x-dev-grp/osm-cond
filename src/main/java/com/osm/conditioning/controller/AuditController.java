package com.osm.conditioning.controller;

import com.xdev.xdevbase.dtos.AuditDto;
import com.osm.conditioning.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ordreConditionement/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<AuditDto>> getAllAudits() {
        return ResponseEntity.ok(auditService.getAllAudits());
    }
}
