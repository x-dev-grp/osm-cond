package com.osm.conditioning.controller;

import com.osm.conditioning.dto.CertificationDto;
import com.osm.conditioning.model.Certification;
import com.osm.conditioning.service.CertificationService;
import com.xdev.xdevbase.apiDTOs.ApiResponse;
import com.xdev.xdevbase.apiDTOs.ApiSingleResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.utils.ExceptionHandler;
import com.xdev.xdevbase.utils.OSMLogger;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/certifications")
public class CertificationController
        extends BaseControllerImpl<Certification, CertificationDto, CertificationDto> {

    private final CertificationService certificationService;

    public CertificationController(
            CertificationService certificationService,
            ModelMapper modelMapper
    ) {
        super(certificationService, modelMapper);
        this.certificationService = certificationService;
    }

    @Override
    protected String getResourceName() {
        return "CERTIFICATION";
    }

    /**
     * GET /api/certifications/fetch/{id}
     */
    @Override
    public ResponseEntity<ApiSingleResponse<Certification, CertificationDto>> findDtoByUuid(
            @PathVariable UUID id
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "findDtoByUuid", id);

        try {
            CertificationDto result = certificationService.findById(id);

            OSMLogger.logMethodExit(this.getClass(), "findDtoByUuid", result);
            OSMLogger.logPerformance(this.getClass(), "findDtoByUuid", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "READ", getResourceName());

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification found successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleSingleException(this.getClass(), "findDtoByUuid", e);
        }
    }

    /**
     * GET /api/certifications/fetchAll
     */
    @Override
    public ResponseEntity<ApiResponse<Certification, CertificationDto>> fetchAll() {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "fetchAll");

        try {
            List<CertificationDto> result = certificationService.findAll();

            OSMLogger.logMethodExit(this.getClass(), "fetchAll", "Found " + result.size() + " certifications");
            OSMLogger.logPerformance(this.getClass(), "fetchAll", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "READ_ALL", getResourceName());

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            "Certifications retrieved successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleException(this.getClass(), "fetchAll", e);
        }
    }

    /**
     * GET /api/certifications/fetchAllPageable
     */
    @Override
    public ResponseEntity<ApiResponse<Certification, CertificationDto>> fetchAllPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "createdDate") String sort,
            @RequestParam(required = false, defaultValue = "DESC") String direction
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "fetchAllPageable", page, size, sort, direction);

        try {
            Page<CertificationDto> result = certificationService.findAll(page, size, sort, direction);

            OSMLogger.logMethodExit(
                    this.getClass(),
                    "fetchAllPageable",
                    "Page " + page + " with " + result.getContent().size() + " certifications"
            );
            OSMLogger.logPerformance(this.getClass(), "fetchAllPageable", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "READ_PAGEABLE", getResourceName());

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            "Certification page retrieved successfully",
                            result.toList()
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleException(this.getClass(), "fetchAllPageable", e);
        }
    }

    /**
     * POST /api/certifications
     */
    @Override
    public ResponseEntity<ApiSingleResponse<Certification, CertificationDto>>  create(
            @RequestBody CertificationDto dto
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "create", dto);

        try {
            CertificationDto result = certificationService.save(dto);

            OSMLogger.logMethodExit(this.getClass(), "create", result);
            OSMLogger.logPerformance(this.getClass(), "create", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "CREATE", getResourceName());
            OSMLogger.logBusinessEvent(
                    this.getClass(),
                    "CERTIFICATION_CREATED",
                    "Created certification with ID: " + result.getId()
            );

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification created successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleSingleException(this.getClass(), "create", e);
        }
    }

    /**
     * PUT /api/certifications
     */
    @Override
    public ResponseEntity<ApiSingleResponse<Certification, CertificationDto>> update(
            @RequestBody CertificationDto dto
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "update", dto);

        try {
            CertificationDto result = certificationService.update(dto);

            OSMLogger.logMethodExit(this.getClass(), "update", result);
            OSMLogger.logPerformance(this.getClass(), "update", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "UPDATE", getResourceName());
            OSMLogger.logBusinessEvent(
                    this.getClass(),
                    "CERTIFICATION_UPDATED",
                    "Updated certification with ID: " + result.getId()
            );

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification updated successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleSingleException(this.getClass(), "update", e);
        }
    }

    /**
     * DELETE /api/certifications/remove/{id}
     * Hard delete.
     */
    @Override
    public ResponseEntity<?> remove(
            @PathVariable UUID id
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "remove", id);

        try {
            certificationService.remove(id);

            OSMLogger.logMethodExit(this.getClass(), "remove");
            OSMLogger.logPerformance(this.getClass(), "remove", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "REMOVE", getResourceName());
            OSMLogger.logBusinessEvent(
                    this.getClass(),
                    "CERTIFICATION_REMOVED",
                    "Removed certification with ID: " + id
            );

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ExceptionHandler.handleException(this.getClass(), "remove", e);
        }
    }

    /**
     * DELETE /api/certifications/delete/{id}
     * Soft delete.
     */
    @Override
    public ResponseEntity<?> delete(
            @PathVariable UUID id
    ) {
        long startTime = System.currentTimeMillis();
        OSMLogger.logMethodEntry(this.getClass(), "delete", id);

        try {
            CertificationDto result = certificationService.delete(id);

            OSMLogger.logMethodExit(this.getClass(), "delete", result);
            OSMLogger.logPerformance(this.getClass(), "delete", startTime, System.currentTimeMillis());
            OSMLogger.logDataAccess(this.getClass(), "DELETE", getResourceName());
            OSMLogger.logBusinessEvent(
                    this.getClass(),
                    "CERTIFICATION_DELETED",
                    "Soft deleted certification with ID: " + id
            );

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification deleted successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleException(this.getClass(), "delete", e);
        }
    }

    /**
     * GET /api/certifications/by-name/{name}
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<ApiSingleResponse<Certification, CertificationDto>> findByName(
            @PathVariable String name
    ) {
        try {
            CertificationDto result = certificationService.findByName(name);

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification found successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleSingleException(this.getClass(), "findByName", e);
        }
    }

    /**
     * GET /api/certifications/by-code/{code}
     */
    @GetMapping("/by-code/{code}")
    public ResponseEntity<ApiSingleResponse<Certification, CertificationDto>> findByCode(
            @PathVariable String code
    ) {
        try {
            CertificationDto result = certificationService.findByCode(code);

            return ResponseEntity.ok(
                    new ApiSingleResponse<>(
                            true,
                            "Certification found successfully",
                            result
                    )
            );
        } catch (Exception e) {
            return ExceptionHandler.handleSingleException(this.getClass(), "findByCode", e);
        }
    }
}