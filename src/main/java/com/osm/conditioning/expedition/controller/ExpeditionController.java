package com.osm.conditioning.expedition.controller;

import com.osm.conditioning.expedition.dto.*;
import com.osm.conditioning.expedition.model.Expedition;
import com.osm.conditioning.expedition.service.ExpeditionService;
import com.xdev.xdevbase.apiDTOs.ApiSingleResponse;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/expeditions")
public class ExpeditionController extends BaseControllerImpl<Expedition, ExpeditionDto, ExpeditionDto> {

    private final ExpeditionService expeditionService;

    public ExpeditionController(
            BaseService<Expedition, ExpeditionDto, ExpeditionDto> baseService,
            ModelMapper modelMapper,
            ExpeditionService expeditionService
    ) {
        super(baseService, modelMapper);
        this.expeditionService = expeditionService;
    }

    @GetMapping
    public ResponseEntity<List<ExpeditionDto>> getAll() {
        return ResponseEntity.ok(expeditionService.findAll());
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiSingleResponse<Expedition, ExpeditionDto>> create(@RequestBody ExpeditionDto dto) {
        ExpeditionCreationRequest request = new ExpeditionCreationRequest();
        request.setProjetId(dto.getProjetId());
        request.setDestination(dto.getDestination());
        request.setPlannedShipDate(dto.getPlannedShipDate());
        request.setNotes(dto.getNotes());
        if (dto.getLines() != null) {
            request.setLines(dto.getLines().stream().map(line -> {
                ExpeditionLineCreateRequest lineRequest = new ExpeditionLineCreateRequest();
                lineRequest.setOfId(line.getOfId());
                lineRequest.setArticleId(line.getArticleId());
                lineRequest.setQuantity(line.getQuantity());
                lineRequest.setVolume(line.getVolume());
                lineRequest.setLotNumber(line.getLotNumber());
                lineRequest.setUnit(line.getUnit());
                return lineRequest;
            }).toList());
        }
        
        ExpeditionDto created = expeditionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiSingleResponse<>(true, "Expedition created successfully", created));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ExpeditionDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(expeditionService.getById(id));
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ExpeditionDto>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(expeditionService.getByProject(projectId));
    }

    @GetMapping("/project/{projectId}/traceability")
    public ResponseEntity<Map<String, Object>> getProjectTraceability(@PathVariable UUID projectId) {
        return ResponseEntity.ok(expeditionService.getProjectTraceability(projectId));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ExpeditionDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(expeditionService.getById(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpeditionDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(expeditionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSingleResponse<Expedition, ExpeditionDto>> update(@PathVariable UUID id, @RequestBody ExpeditionDto dto) {
        ExpeditionUpdateRequest request = new ExpeditionUpdateRequest();
        request.setDestination(dto.getDestination());
        request.setPlannedShipDate(dto.getPlannedShipDate());
        request.setNotes(dto.getNotes());
        request.setCarrierName(dto.getCarrierName());
        request.setDriverName(dto.getDriverName());
        request.setTruckNumber(dto.getTruckNumber());
        request.setTrackingNumber(dto.getTrackingNumber());
        request.setIncoterm(dto.getIncoterm());
        
        ExpeditionDto updated = expeditionService.update(id, request);
        return ResponseEntity.ok(new ApiSingleResponse<>(true, "Expedition updated successfully", updated));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<ExpeditionDto> addLine(
            @PathVariable UUID id,
            @Valid @RequestBody ExpeditionLineCreateRequest request
    ) {
        ExpeditionDto dto = expeditionService.addLine(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public ResponseEntity<ExpeditionDto> removeLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId
    ) {
        return ResponseEntity.ok(expeditionService.removeLine(id, lineId));
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<ExpeditionDto> ready(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.markReady(id, request));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ExpeditionDto> validate(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.validate(id, request));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ExpeditionDto> ship(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.ship(id, request));
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<ExpeditionDto> deliver(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.deliver(id, request));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ExpeditionDto> close(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.close(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ExpeditionDto> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) ExpeditionActionRequest request
    ) {
        return ResponseEntity.ok(expeditionService.cancel(id, request));
    }

    @GetMapping("/{id}/qr-image")
    public ResponseEntity<byte[]> getQrImage(@PathVariable UUID id) {
        Expedition entity = expeditionService.getEntityById(id);

        if (entity.getQrHex() == null || entity.getQrHex().isBlank()) {
            byte[] image = expeditionService.generateQrImageFromEntity(entity);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        }

        byte[] image = expeditionService.generateQrImage(entity.getQrHex());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    @Override
    @GetMapping("/resolve/{publicCode}")
    public ResponseEntity<?> resolve(@PathVariable String publicCode) {
        try {
            QrResolveResponse resolveResponse = expeditionService.resolve(publicCode);
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
        return "EXPEDITION";
    }
}
