package com.osm.production.controller;

import com.osm.production.dto.AjustementConsommationDto;
import com.osm.production.dto.OrdreFabricationDto;
import com.osm.production.dto.SaisieProductionDto;
import com.osm.production.model.OrdreFabrication;
import com.osm.production.service.OFService;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.services.BaseService;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/of")
public class OFController extends BaseControllerImpl<OrdreFabrication, OrdreFabricationDto, OrdreFabricationDto> {

    @Autowired
    private OFService ofService;

    public OFController(BaseService<OrdreFabrication, OrdreFabricationDto, OrdreFabricationDto> baseService,
                        ModelMapper modelMapper) {
        super(baseService, modelMapper);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOFById(@PathVariable UUID id) {
        try {
            OrdreFabricationDto of = ofService.findById(id);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllOF() {
        try {
            List<OrdreFabricationDto> list = ofService.findAll();
            return ResponseEntity.ok(list);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrdreFabricationDto>> creerOF(@RequestBody OrdreFabricationDto dto) {
        try {
            OrdreFabricationDto created = ofService.creerOF(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "OF créé avec succès", created));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}/demarrer")
    public ResponseEntity<?> demarrerOF(@PathVariable UUID id) {
        try {
            OrdreFabricationDto of = ofService.demarrerOF(id);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/pause")
    public ResponseEntity<?> pauseOF(@PathVariable UUID id) {
        try {
            OrdreFabricationDto of = ofService.mettreEnPause(id);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reprise")
    public ResponseEntity<?> reprendreOF(@PathVariable UUID id) {
        try {
            OrdreFabricationDto of = ofService.reprendreOF(id);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/cloturer")
    public ResponseEntity<?> cloturerOF(@PathVariable UUID id) {
        try {
            OrdreFabricationDto of = ofService.cloturerOF(id);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/production")
    public ResponseEntity<?> saisirProduction(@PathVariable UUID id, @RequestBody SaisieProductionDto dto) {
        try {
            OrdreFabricationDto of = ofService.saisirProduction(id, dto);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/ajustements")
    public ResponseEntity<?> ajusterConsommation(@PathVariable UUID id, @RequestBody AjustementConsommationDto ajustement) {
        try {
            OrdreFabricationDto of = ofService.ajusterConsommation(id, ajustement);
            return ResponseEntity.ok(of);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/qr-image")
    public ResponseEntity<byte[]> getQrImage(@PathVariable UUID id) {
        OrdreFabrication entity = ofService.getEntityById(id);
        if (entity.getQrHex() == null || entity.getQrHex().isBlank()) {
            byte[] image = ofService.generateQrImageFromEntity(entity);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        }

        byte[] image = ofService.generateQrImage(entity.getQrHex());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }


    @Override
    public ResponseEntity<?> resolve(@PathVariable String publicCode) {
        try {
            QrResolveResponse resolveResponse = ofService.resolve(publicCode);
            return ResponseEntity.ok(resolveResponse);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "code", "NOT_FOUND"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "code", "INVALID_FORMAT"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur interne: " + e.getMessage()));
        }
    }


    @Override
    protected String getResourceName() {
        return "OF";
    }

}
