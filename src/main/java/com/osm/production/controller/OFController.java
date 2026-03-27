package com.osm.production.controller;

import com.osm.production.dto.AjustementConsommationDto;
import com.osm.production.dto.OrdreFabricationtDto;
import com.osm.production.dto.SaisieProductionDto;
import com.osm.production.model.OrdreFabrication;
import com.osm.production.service.OFService;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.services.BaseService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/of")
public class OFController extends BaseControllerImpl<OrdreFabrication, OrdreFabricationtDto, OrdreFabricationtDto> {

    @Autowired
    private OFService ofService;

    public OFController(BaseService<OrdreFabrication, OrdreFabricationtDto, OrdreFabricationtDto> baseService, ModelMapper modelMapper) {
        super(baseService, modelMapper);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdreFabricationtDto> getOFById(@PathVariable UUID id) {
        OrdreFabricationtDto of = ofService.findById(id);
        return ResponseEntity.ok(of);
    }

    @GetMapping("/all")
    public ResponseEntity<List<OrdreFabricationtDto>> getAllOF() {
        List<OrdreFabricationtDto> list = ofService.findAll();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/create")
    public ResponseEntity<OrdreFabricationtDto> creerOF(@RequestBody OrdreFabricationtDto dto) {
        OrdreFabricationtDto created = ofService.creerOF(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/demarrer")
    public ResponseEntity<OrdreFabricationtDto> demarrerOF(@PathVariable UUID id) {
        OrdreFabricationtDto of = ofService.demarrerOF(id);
        return ResponseEntity.ok(of);
    }

    @PutMapping("/{id}/pause")
    public ResponseEntity<OrdreFabricationtDto> pauseOF(@PathVariable UUID id) {
        OrdreFabricationtDto of = ofService.mettreEnPause(id);
        return ResponseEntity.ok(of);
    }

    @PutMapping("/{id}/reprise")
    public ResponseEntity<OrdreFabricationtDto> reprendreOF(@PathVariable UUID id) {
        OrdreFabricationtDto of = ofService.reprendreOF(id);
        return ResponseEntity.ok(of);
    }
    @PutMapping("/{id}/cloturer")
    public ResponseEntity<OrdreFabricationtDto> cloturerOF(@PathVariable UUID id) {
        OrdreFabricationtDto of = ofService.cloturerOF(id);
        return ResponseEntity.ok(of);
    }

    @PutMapping("/{id}/production")
    public ResponseEntity<OrdreFabricationtDto> saisirProduction(@PathVariable UUID id, @RequestBody SaisieProductionDto dto) {
        OrdreFabricationtDto of = ofService.saisirProduction(id, dto);
        return ResponseEntity.ok(of);
    }

    @PutMapping("/{id}/ajustements")
    public ResponseEntity<OrdreFabricationtDto> ajusterConsommation(@PathVariable UUID id, @RequestBody List<AjustementConsommationDto> ajustements) {
        OrdreFabricationtDto of = ofService.ajusterConsommation(id, ajustements);
        return ResponseEntity.ok(of);
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
    protected String getResourceName() {
        return "OF";
    }
}
