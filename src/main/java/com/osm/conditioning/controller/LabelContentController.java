package com.osm.conditioning.controller;

import com.osm.conditioning.service.LabelContentService;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.LabelContentDto;
import com.xdev.communicator.models.shared.LabelContentUpdateRequestDto;
import com.xdev.communicator.models.shared.LabelExportDto;
import com.xdev.communicator.models.shared.LabelGenerateRequestDto;
import com.xdev.xdevbase.utils.OSMLogger;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/labels")
public class LabelContentController {

    private final LabelContentService labelContentService;

    public LabelContentController(LabelContentService labelContentService) {
        this.labelContentService = labelContentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabelContentDto>>> getAll() {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "getAll");

        try {
            List<LabelContentDto> result = labelContentService.getAll();

            OSMLogger.logMethodExit(this.getClass(), "getAll", result);
            OSMLogger.logPerformance(this.getClass(), "getAll", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Liste des etiquettes recuperee avec succes", result);
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "getAll", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la lecture des etiquettes");
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<LabelContentDto>> generate(@RequestBody LabelGenerateRequestDto request) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "generate", request);

        try {
            LabelContentDto result = labelContentService.generate(request);

            OSMLogger.logMethodExit(this.getClass(), "generate", result);
            OSMLogger.logPerformance(this.getClass(), "generate", startTime, System.currentTimeMillis());

            return success(HttpStatus.CREATED, "Contenu d'etiquette genere avec succes", result);
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "generate", e);
            return failure(resolveStatus(e), e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "generate", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la generation d'etiquette");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LabelContentDto>> getById(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "getById", id);

        try {
            LabelContentDto result = labelContentService.getById(id);

            OSMLogger.logMethodExit(this.getClass(), "getById", result);
            OSMLogger.logPerformance(this.getClass(), "getById", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Contenu d'etiquette recupere avec succes", result);
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "getById", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "getById", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la lecture de l'etiquette");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LabelContentDto>> update(
            @PathVariable UUID id,
            @RequestBody LabelContentUpdateRequestDto request
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "update", id, request);

        try {
            LabelContentDto result = labelContentService.update(id, request);

            OSMLogger.logMethodExit(this.getClass(), "update", result);
            OSMLogger.logPerformance(this.getClass(), "update", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Contenu d'etiquette mis a jour avec succes", result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            OSMLogger.logException(this.getClass(), "update", e);
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "update", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "update", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la mise a jour de l'etiquette");
        }
    }

    @PostMapping("/{id}/draft")
    public ResponseEntity<ApiResponse<LabelContentDto>> markAsDraft(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "markAsDraft", id);

        try {
            LabelContentDto result = labelContentService.markAsDraft(id);

            OSMLogger.logMethodExit(this.getClass(), "markAsDraft", result);
            OSMLogger.logPerformance(this.getClass(), "markAsDraft", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Etiquette remise en brouillon avec succes", result);
        } catch (IllegalStateException e) {
            OSMLogger.logException(this.getClass(), "markAsDraft", e);
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "markAsDraft", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "markAsDraft", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors du changement de statut en brouillon");
        }
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ApiResponse<LabelContentDto>> finalizeLabel(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "finalizeLabel", id);

        try {
            LabelContentDto result = labelContentService.approve(id);

            OSMLogger.logMethodExit(this.getClass(), "finalizeLabel", result);
            OSMLogger.logPerformance(this.getClass(), "finalizeLabel", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Contenu d'etiquette finalise avec succes", result);
        } catch (IllegalStateException e) {
            OSMLogger.logException(this.getClass(), "finalizeLabel", e);
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "finalizeLabel", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "finalizeLabel", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la finalisation de l'etiquette");
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<ApiResponse<LabelExportDto>> export(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "export", id);

        try {
            LabelExportDto result = labelContentService.export(id);

            OSMLogger.logMethodExit(this.getClass(), "export", result);
            OSMLogger.logPerformance(this.getClass(), "export", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Export d'etiquette prepare avec succes", result);
        } catch (IllegalStateException e) {
            OSMLogger.logException(this.getClass(), "export", e);
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "export", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "export", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de l'export de l'etiquette");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "delete", id);

        try {
            labelContentService.delete(id);

            OSMLogger.logMethodExit(this.getClass(), "delete", null);
            OSMLogger.logPerformance(this.getClass(), "delete", startTime, System.currentTimeMillis());

            return success(HttpStatus.OK, "Etiquette supprimee avec succes", null);
        } catch (IllegalStateException e) {
            OSMLogger.logException(this.getClass(), "delete", e);
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "delete", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "delete", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la suppression de l'etiquette");
        }
    }

    private HttpStatus resolveStatus(Exception exception) {
        return exception instanceof EntityNotFoundException
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
    }

    private <T> ResponseEntity<ApiResponse<T>> success(HttpStatus status, String message, T data) {
        return ResponseEntity
                .status(status)
                .body(new ApiResponse<>(true, message, data));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiResponse<>(false, message, null));
    }
}