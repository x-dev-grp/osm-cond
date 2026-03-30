package com.osm.production.controller;

import com.osm.production.dto.SaisieProductionDto;
import com.osm.production.dto.SyncRequestDto;
import com.osm.production.service.OFService;
import com.osm.production.service.SyncService;
import com.xdev.communicator.models.shared.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/mobile")
public class MobileSyncController {

    private final SyncService syncService;
    private final OFService ofService;

    public MobileSyncController(SyncService syncService, OFService ofService) {
        this.syncService = syncService;
        this.ofService = ofService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncOperation(@RequestBody SyncRequestDto request) {
        try {
            syncService.processSync(request);
            return ResponseEntity.ok(new ApiResponse<>(true, "Synchronisation réussie", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

}