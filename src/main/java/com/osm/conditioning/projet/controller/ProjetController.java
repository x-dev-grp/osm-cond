package com.osm.conditioning.projet.controller;

import com.osm.conditioning.projet.dto.ProjetDto;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.service.ProjetService;
import com.xdev.xdevbase.apiDTOs.ApiResponse;
import com.xdev.xdevbase.apiDTOs.ApiSingleResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.models.Action;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/projets")
public class ProjetController extends BaseControllerImpl<Projet, ProjetDto, ProjetDto> {

    private final ProjetService projetService;

    public ProjetController(
            BaseService<Projet, ProjetDto, ProjetDto> baseService,
            ModelMapper modelMapper,
            ProjetService projetService
    ) {
        super(baseService, modelMapper);
        this.projetService = projetService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjetDto> getProjetById(@PathVariable UUID id) {
        ProjetDto projet = projetService.findById(id);
        return ResponseEntity.ok(attachPermittedActions(projet));
    }

    @GetMapping("/all")
    public ResponseEntity<List<ProjetDto>> getAllProjets() {
        return ResponseEntity.ok(attachPermittedActions(projetService.findAll()));
    }

    @PostMapping("/create")
    public ResponseEntity<ProjetDto> createProjetManual(@Valid @RequestBody ProjetDto dto) {
        ProjetDto created = projetService.create(dto);
        return new ResponseEntity<>(attachPermittedActions(created), HttpStatus.CREATED);
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ProjetDto> getProjetByCode(@PathVariable String code) {
        ProjetDto projet = projetService.findByCode(code);
        return ResponseEntity.ok(attachPermittedActions(projet));
    }

    @GetMapping("/unique/{code}")
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> getProjetByUniqueCode(@PathVariable String code) {
        try {
            ProjetDto projet = projetService.findByUniqueCode(code);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Projet trouvé", attachPermittedActions(projet))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne", null));
        }
    }

    @Override
    public ResponseEntity<ApiResponse<Projet, ProjetDto>> fetchAll() {
        try {
            List<ProjetDto> projets = projetService.findAll();
            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Projets récupérés avec succès", attachPermittedActions(projets))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Erreur lors de la récupération des projets", null));
        }
    }

    @Override
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> findDtoByUuid(UUID id) {
        try {
            ProjetDto projet = projetService.findById(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Projet récupéré avec succès", attachPermittedActions(projet))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne", null));
        }
    }

    @Override
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> create(
            @RequestBody ProjetDto dto
    ) {
        try {
            ProjetDto created = projetService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiSingleResponse<>(true, "Projet créé avec succès", attachPermittedActions(created)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la création du projet", null));
        }
    }

    @Override
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> update(
            @Valid @RequestBody ProjetDto dto
    ) {
        try {
            if (dto == null || dto.getId() == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiSingleResponse<>(false, "L'id du projet est obligatoire pour la mise à jour", null));
            }

            ProjetDto updated = projetService.update(dto.getId(), dto);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Projet mis à jour avec succès", attachPermittedActions(updated))
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la mise à jour", null));
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> cancel(@PathVariable UUID id) {
        try {
            ProjetDto cancelled = projetService.cancel(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Projet annulé avec succès", attachPermittedActions(cancelled))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de l'annulation", null));
        }
    }

    @Override
    public ResponseEntity<?> delete(UUID id) {
        try {
            ProjetDto deleted = projetService.delete(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Projet supprimé avec succès", attachPermittedActions(deleted))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<Projet, ProjetDto>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<Projet, ProjetDto>(false, "Erreur interne lors de la suppression", null));
        }
    }

    @Override
    public ResponseEntity<?> remove(UUID id) {
        try {
            projetService.remove(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<Projet, ProjetDto>(true, "Projet supprimé définitivement avec succès", null)
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<Projet, ProjetDto>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<Projet, ProjetDto>(false, "Erreur interne lors de la suppression définitive", null));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String statut
    ) {
        try {
            ProjetDto updated = projetService.updateStatus(id, statut);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Statut du projet mis à jour avec succès", attachPermittedActions(updated))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la mise à jour du statut", null));
        }
    }

    @PutMapping("/status-by-code/{code}")
    public ResponseEntity<ApiSingleResponse<Projet, ProjetDto>> updateStatusByCode(
            @PathVariable String code,
            @RequestParam String statut
    ) {
        try {
            ProjetDto updated = projetService.updateStatusByCode(code, statut);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "Statut du projet mis à jour avec succès", attachPermittedActions(updated))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la mise à jour du statut", null));
        }
    }

    @GetMapping("/{id}/qr-image")
    public ResponseEntity<byte[]> getQrImage(@PathVariable UUID id) {
        Projet entity = projetService.getEntityById(id);

        if (entity.getQrHex() == null || entity.getQrHex().isBlank()) {
            byte[] image = projetService.generateQrImageFromEntity(entity);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        }

        byte[] image = projetService.generateQrImage(entity.getQrHex());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    @Override
    @GetMapping("/resolve/{publicCode}")
    public ResponseEntity<QrResolveResponse> resolve(@PathVariable String publicCode) {
        try {
            QrResolveResponse resolveResponse = projetService.resolve(publicCode);
            return ResponseEntity.ok(resolveResponse);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @Override
    protected String getResourceName() {
        return "PROJET";
    }

    /**
     * Avoid ModelMapper ProjetDto -> Projet mapping: nested client.id and clientId both target setClient().
     */
    @Override
    protected ProjetDto attachPermittedActions(ProjetDto dto) {
        if (dto == null) {
            return null;
        }
        Set<Action> actions = projetService.actionsMapping(new Projet());
        return attachPermittedActions(dto, getResourceName(), actions);
    }

    @Override
    protected List<ProjetDto> attachPermittedActions(List<ProjetDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return dtos;
        }
        Set<Action> actions = projetService.actionsMapping(new Projet());
        return attachPermittedActions(dtos, getResourceName(), actions);
    }
}