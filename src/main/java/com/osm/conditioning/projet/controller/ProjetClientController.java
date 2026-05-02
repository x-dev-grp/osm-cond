package com.osm.conditioning.projet.controller;

import com.osm.conditioning.projet.dto.ProjetClientDto;
import com.osm.conditioning.projet.entity.ProjetClient;
import com.osm.conditioning.projet.service.ProjetClientService;
import com.xdev.xdevbase.apiDTOs.ApiResponse;
import com.xdev.xdevbase.apiDTOs.ApiSingleResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.services.BaseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/projet_client")
public class ProjetClientController extends BaseControllerImpl<ProjetClient, ProjetClientDto, ProjetClientDto> {

    private final ProjetClientService projetClientService;

    public ProjetClientController(
            BaseService<ProjetClient, ProjetClientDto, ProjetClientDto> baseService,
            ModelMapper modelMapper,
            ProjetClientService projetClientService
    ) {
        super(baseService, modelMapper);
        this.projetClientService = projetClientService;
    }

    @Override
    public ResponseEntity<ApiResponse<ProjetClient, ProjetClientDto>> fetchAll() {
        try {
            List<ProjetClientDto> clients = projetClientService.findAll();
            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Clients récupérés avec succès", clients)
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Erreur lors de la récupération des clients", null));
        }
    }

    @Override
    public ResponseEntity<ApiSingleResponse<ProjetClient, ProjetClientDto>> findDtoByUuid(UUID id) {
        try {
            ProjetClientDto client = projetClientService.findById(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "ProjetClient trouvé", client)
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
    @PostMapping
    public ResponseEntity<ApiSingleResponse<ProjetClient, ProjetClientDto>> create(
            @Valid @RequestBody ProjetClientDto dto
    ) {
        try {
            ProjetClientDto created = projetClientService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiSingleResponse<>(true, "ProjetClient créé avec succès", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la création", null));
        }
    }

    @Override
    public ResponseEntity<ApiSingleResponse<ProjetClient, ProjetClientDto>> update(
            @Valid @RequestBody ProjetClientDto dto
    ) {
        try {
            if (dto == null || dto.getId() == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiSingleResponse<>(false, "L'id du ProjetClient est obligatoire pour la mise à jour", null));
            }

            ProjetClientDto updated = projetClientService.update(dto.getId(), dto);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "ProjetClient mis à jour avec succès", updated)
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiSingleResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<>(false, "Erreur interne lors de la mise à jour", null));
        }
    }

    @Override
    public ResponseEntity<?> delete(UUID id) {
        try {
            ProjetClientDto deleted = projetClientService.delete(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<>(true, "ProjetClient supprimé avec succès", deleted)
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<ProjetClient, ProjetClientDto>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<ProjetClient, ProjetClientDto>(false, "Erreur interne lors de la suppression", null));
        }
    }

    @Override
    public ResponseEntity<?> remove(UUID id) {
        try {
            projetClientService.remove(id);
            return ResponseEntity.ok(
                    new ApiSingleResponse<ProjetClient, ProjetClientDto>(true, "ProjetClient supprimé définitivement avec succès", null)
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiSingleResponse<ProjetClient, ProjetClientDto>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiSingleResponse<ProjetClient, ProjetClientDto>(false, "Erreur interne lors de la suppression définitive", null));
        }
    }

    @Override
    protected String getResourceName() {
        return "PROJET_CLIENT";
    }
}