package com.osm.conditioning.controller;


import com.osm.conditioning.dto.QCControlPointDTO;
import com.osm.conditioning.dto.QCPlanDTO;
import com.osm.conditioning.dto.QCResultDTO;
import com.osm.conditioning.model.QCPlan;
import com.osm.conditioning.service.QCPlanService;
import com.osm.conditioning.service.QCResultService;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.xdevbase.controllers.impl.BaseControllerImpl;
import com.xdev.xdevbase.models.Action;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/ordreConditionement/qualite")
public class QualityController extends BaseControllerImpl<QCPlan, QCPlanDTO, QCPlanDTO> {

    private final QCPlanService planService;
    private final QCResultService resultService;
    private static final Set<Action> QUALITY_ACTIONS = Set.of(
            Action.READ,
            Action.CREATE,
            Action.UPDATE,
            Action.DELETE,
            Action.VALIDATE,
            Action.UPDATE_STATUS,
            Action.GEN_PDF
    );

    public QualityController(QCPlanService planService, QCResultService resultService, ModelMapper modelMapper) {
        super(planService, modelMapper);
        this.planService = planService;
        this.resultService = resultService;
    }

    @PostMapping("/plans/of/{ofId}/create")
    public ResponseEntity<ApiResponse<QCPlanDTO>> createPlan(
            @PathVariable UUID ofId,
            @RequestParam String titre) {
        try {
            QCPlanDTO plan = planService.createPlan(ofId, titre);
            return ResponseEntity.ok(new ApiResponse<>(true, "Plan créé", attachQualityActions(plan)));
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
            return ResponseEntity.ok(new ApiResponse<>(true, "Point ajouté", attachQualityActions(point)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/plans/of/{ofId}/points/active")
    public ResponseEntity<ApiResponse<List<QCControlPointDTO>>> getActivePoints(@PathVariable UUID ofId) {
        try {
            List<QCControlPointDTO> points = planService.getPointsForOF(ofId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Points récupérés", attachQualityActions(points)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/plans/all")
    public ResponseEntity<ApiResponse<List<QCPlanDTO>>> getAllPlans() {
        List<QCPlanDTO> plans = planService.findAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "Plans récupérés", attachPermittedActions(plans)));
    }


    @PostMapping("/resultats/add")
    public ResponseEntity<ApiResponse<QCResultDTO>> enregistrerResultat(@RequestBody QCResultDTO dto) {
        try {
            QCResultDTO resultat = resultService.enregistrerResultat(dto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Résultat enregistré", attachQualityActions(resultat)));
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
        return ResponseEntity.ok(new ApiResponse<>(true, "Historique récupéré", attachQualityActions(historique)));
    }

    @GetMapping("/plans/of/{ofId}")
    public ResponseEntity<ApiResponse<QCPlanDTO>> getPlanByOfId(@PathVariable UUID ofId) {
        try {
            QCPlanDTO plan = planService.findActivePlanByOfId(ofId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Plan récupéré", attachPermittedActions(plan)));
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

    private <DTO extends com.xdev.xdevbase.dtos.BaseDto<?>> DTO attachQualityActions(DTO dto) {
        return attachPermittedActions(dto, getResourceName(), QUALITY_ACTIONS);
    }

    private <DTO extends com.xdev.xdevbase.dtos.BaseDto<?>> List<DTO> attachQualityActions(List<DTO> dtos) {
        return attachPermittedActions(dtos, getResourceName(), QUALITY_ACTIONS);
    }

    @Override
    protected String getResourceName() {
        return "QUALITY";
    }

    @Override
    public ResponseEntity<?> resolve(String publicCode) {
        return null;
    }
}
