package com.osm.conditioning.controller;


import com.osm.conditioning.dto.QCControlPointDTO;
import com.osm.conditioning.dto.QCPlanDTO;
import com.osm.conditioning.dto.QCResultDTO;
import com.osm.conditioning.service.QCPlanService;
import com.osm.conditioning.service.QCResultService;
import com.xdev.communicator.models.shared.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/qualite")
public class QualityController {

    private final QCPlanService planService;
    private final QCResultService resultService;

    public QualityController(QCPlanService planService, QCResultService resultService) {
        this.planService = planService;
        this.resultService = resultService;
    }

    @PostMapping("/plans/of/{ofId}/create")
    public ResponseEntity<ApiResponse<QCPlanDTO>> createPlan(
            @PathVariable UUID ofId,
            @RequestParam String titre) {
        try {
            QCPlanDTO plan = planService.createPlan(ofId, titre);
            return ResponseEntity.ok(new ApiResponse<>(true, "Plan créé", plan));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/plans/{planId}/points/addControlPoint")
    public ResponseEntity<ApiResponse<QCControlPointDTO>> addControlPoint(
            @PathVariable UUID planId,
            @RequestBody QCControlPointDTO dto) {
        try {
            QCControlPointDTO point = planService.addControlPoint(planId, dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Point ajouté", point));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/plans/of/{ofId}/points/active")
    public ResponseEntity<ApiResponse<List<QCControlPointDTO>>> getActivePoints(@PathVariable UUID ofId) {
        try {
            List<QCControlPointDTO> points = planService.getPointsForOF(ofId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Points récupérés", points));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/plans/all")
    public ResponseEntity<ApiResponse<List<QCPlanDTO>>> getAllPlans() {
        List<QCPlanDTO> plans = planService.findAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "Plans récupérés", plans));
    }


    @PostMapping("/resultats/add")
    public ResponseEntity<ApiResponse<QCResultDTO>> enregistrerResultat(@RequestBody QCResultDTO dto) {
        try {
            QCResultDTO resultat = resultService.enregistrerResultat(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Résultat enregistré", resultat));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PutMapping("/resultats/of/{ofId}/debloquer")
    public ResponseEntity<ApiResponse<Void>> debloquerOF(@PathVariable UUID ofId) {
        try {
            resultService.verifierEtDebloquerOF(ofId);
            return ResponseEntity.ok(new ApiResponse<>(true, "OF débloqué", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/resultats/of/{ofId}/historique")
    public ResponseEntity<ApiResponse<List<QCResultDTO>>> getHistoriqueOF(@PathVariable UUID ofId) {
        List<QCResultDTO> historique = resultService.getHistoriqueOF(ofId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Historique récupéré", historique));
    }

    @GetMapping("/plans/of/{ofId}")
    public ResponseEntity<ApiResponse<QCPlanDTO>> getPlanByOfId(@PathVariable UUID ofId) {
        try {
            QCPlanDTO plan = planService.findActivePlanByOfId(ofId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Plan récupéré", plan));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
    @DeleteMapping("/plans/points/{pointId}")
    public ResponseEntity<ApiResponse<Void>> deleteControlPoint(@PathVariable UUID pointId) {
        try {
            planService.deleteControlPoint(pointId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Point supprimé", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}