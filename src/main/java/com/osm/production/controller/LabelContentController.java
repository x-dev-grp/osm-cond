package com.osm.production.controller;

import com.osm.production.service.LabelContentService;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.LabelContentDto;
import com.xdev.communicator.models.shared.LabelContentUpdateRequestDto;
import com.xdev.communicator.models.shared.LabelExportDto;
import com.xdev.communicator.models.shared.LabelGenerateRequestDto;
import com.xdev.xdevbase.utils.OSMLogger;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/labels")
public class LabelContentController {

    private final LabelContentService labelContentService;

    public LabelContentController(LabelContentService labelContentService) {
        this.labelContentService = labelContentService;
    }

    //pts d'entrer pour cree une nouvelle etiquet
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<LabelContentDto>> generate(@RequestBody LabelGenerateRequestDto request) {


        try {
            LabelContentDto result = labelContentService.generate(request);//

            return success(HttpStatus.CREATED, "Contenu d'etiquette genere avec succes", result);
        } catch (IllegalArgumentException | IllegalStateException | EntityNotFoundException e) {
            return failure(resolveStatus(e), e.getMessage());
        } catch (Exception e) {
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la generation d'etiquette");
        }
    }

    //recup tt les detail par id
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
    public ResponseEntity<ApiResponse<LabelContentDto>> update(@PathVariable UUID id, @RequestBody LabelContentUpdateRequestDto request) {
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

    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<LabelContentDto>> validate(@PathVariable UUID id) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "validate", id);

        try {
            LabelContentDto result = labelContentService.validate(id);
            OSMLogger.logMethodExit(this.getClass(), "validate", result);
            OSMLogger.logPerformance(this.getClass(), "validate", startTime, System.currentTimeMillis());
            return success(HttpStatus.OK, "Contenu d'etiquette valide avec succes", result);
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "validate", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "validate", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de la validation de l'etiquette");
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
        } catch (EntityNotFoundException e) {
            OSMLogger.logException(this.getClass(), "export", e);
            return failure(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "export", e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne lors de l'export de l'etiquette");
        }
    }

    private HttpStatus resolveStatus(Exception exception) {
        return exception instanceof EntityNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    }

    private <T> ResponseEntity<ApiResponse<T>> success(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(new ApiResponse<>(true, message, data));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(false, message, null));
    }
}
