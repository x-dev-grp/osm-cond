package com.osm.conditioning.shipping.controller;

import com.osm.conditioning.shipping.dto.*;
import com.osm.conditioning.shipping.model.ShippingInfo;
import com.osm.conditioning.shipping.service.ShippingInfoService;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.services.BaseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/shipping")
public class ShippingInfoController extends BaseControllerImpl<ShippingInfo, ShippingInfoDto, ShippingInfoDto> {

    private final ShippingInfoService shippingInfoService;

    public ShippingInfoController(
            BaseService<ShippingInfo, ShippingInfoDto, ShippingInfoDto> baseService,
            ModelMapper modelMapper,
            ShippingInfoService shippingInfoService
    ) {
        super(baseService, modelMapper);
        this.shippingInfoService = shippingInfoService;
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ShippingInfoDto> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(shippingInfoService.getOrCreateByProjectId(projectId));
    }

    @GetMapping("/{shippingId}")
    public ResponseEntity<ShippingInfoDto> getById(@PathVariable UUID shippingId) {
        return ResponseEntity.ok(shippingInfoService.getById(shippingId));
    }

    @PutMapping("/project/{projectId}")
    public ResponseEntity<ShippingInfoDto> upsertByProject(
            @PathVariable UUID projectId,
            @RequestBody ShippingInfoUpsertRequest request
    ) {
        return ResponseEntity.ok(shippingInfoService.upsertProjectShipping(projectId, request));
    }

    @PostMapping("/project/{projectId}/lines")
    public ResponseEntity<ShippingInfoDto> addLine(
            @PathVariable UUID projectId,
            @Valid @RequestBody ShippingLineCreateRequest request
    ) {
        ShippingInfoDto dto = shippingInfoService.addLine(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{shippingId}/lines/{lineId}")
    public ResponseEntity<ShippingInfoDto> removeLine(
            @PathVariable UUID shippingId,
            @PathVariable UUID lineId
    ) {
        return ResponseEntity.ok(shippingInfoService.removeLine(shippingId, lineId));
    }

    @PostMapping("/{shippingId}/events")
    public ResponseEntity<ShippingInfoDto> addEvent(
            @PathVariable UUID shippingId,
            @Valid @RequestBody ShippingEventCreateRequest request
    ) {
        return ResponseEntity.ok(shippingInfoService.addEvent(shippingId, request));
    }

    @PutMapping("/{shippingId}/status")
    public ResponseEntity<ShippingInfoDto> updateStatus(
            @PathVariable UUID shippingId,
            @Valid @RequestBody ShippingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(shippingInfoService.updateStatus(shippingId, request));
    }

    @GetMapping("/{shippingId}/qr-image")
    public ResponseEntity<byte[]> getQrImage(@PathVariable UUID shippingId) {
        ShippingInfo entity = shippingInfoService.getEntityById(shippingId);
        if (entity.getQrHex() == null || entity.getQrHex().isBlank()) {
            byte[] image = shippingInfoService.generateQrImageFromEntity(entity);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        }

        byte[] image = shippingInfoService.generateQrImage(entity.getQrHex());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    @Override
    @GetMapping("/resolve/{publicCode}")
    public ResponseEntity<?> resolve(@PathVariable String publicCode) {
        try {
            QrResolveResponse resolveResponse = shippingInfoService.resolve(publicCode);
            return ResponseEntity.ok(resolveResponse);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "code", "NOT_FOUND"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "code", "INVALID_FORMAT"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }

    @Override
    protected String getResourceName() {
        return "SHIPPING";
    }
}
