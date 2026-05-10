package com.osm.conditioning.service;


import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.client.SecurityClient;
import com.osm.conditioning.dto.QCResultDTO;
import com.osm.conditioning.Enum.ControlType;
import com.osm.conditioning.Enum.QualityStatus;
import com.osm.conditioning.Enum.ResultStatus;
import com.osm.conditioning.model.*;
import com.osm.conditioning.repository.*;
import com.xdev.communicator.models.shared.OSMUserDTO;
import com.xdev.onsignalNotifcations.dto.NotificationRequest;
import com.xdev.onsignalNotifcations.impl.OneSignalServiceImpl;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.xdev.communicator.feignServices.BaseFeignService.log;

@Service
public class QCResultService extends BaseServiceImpl<QCResult, QCResultDTO, QCResultDTO> {

    private final QCResultRepository resultRepository;
    private final QCControlPointRepository controlPointRepository;
    private final OrdreFabricationRepository ofRepository;
    private final ModelMapper modelMapper;
    private final QCPlanRepository qcPlanRepository;
    private final OFService ofService;
    // === AJOUT NOTIFICATIONS ===
    private final OneSignalServiceImpl oneSignalService;
    private final SecurityClient securityClient;

    public QCResultService(BaseRepository<QCResult> repository,
                           QCResultRepository resultRepository,
                           QCControlPointRepository controlPointRepository,
                           OrdreFabricationRepository ofRepository,
                           ModelMapper modelMapper,
                           QCPlanRepository qcPlanRepository,
                           OFService ofService, OneSignalServiceImpl oneSignalService,
                           SecurityClient securityClient) {
        super(repository, modelMapper);
        this.resultRepository = resultRepository;
        this.controlPointRepository = controlPointRepository;
        this.ofRepository = ofRepository;
        this.modelMapper = modelMapper;
        this.qcPlanRepository = qcPlanRepository;
        this.ofService = ofService;
        this.oneSignalService = oneSignalService;
        this.securityClient = securityClient;
    }

    @Transactional
    public QCResultDTO enregistrerResultat(QCResultDTO dto) {
        QCControlPoint point = controlPointRepository.findById(dto.getControlPointId())
                .orElseThrow(() -> new RuntimeException("Point de contrôle inconnu"));
        OrdreFabrication of = ofRepository.findById(dto.getOfId())
                .orElseThrow(() -> new RuntimeException("OF inconnu"));

        QCResult result = modelMapper.map(dto, QCResult.class);
        result.setControlPoint(point);
        result.setOf(of);
        result.setDateControle(LocalDateTime.now());
        if (point.getType() == ControlType.NUMERIC && dto.getStatut() == null) {
            try {
                Double val = Double.parseDouble(dto.getValeur());
                if (val >= point.getMinValue() && val <= point.getMaxValue()) {
                    result.setStatut(ResultStatus.OK);
                } else {
                    result.setStatut(ResultStatus.NOK);
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("Valeur numérique invalide : " + dto.getValeur());
            }
        } else {
            result.setStatut(dto.getStatut());
        }
        result = resultRepository.save(result);

        if (result.getStatut() == ResultStatus.NOK) {
            point.setBlocking(true);
            controlPointRepository.save(point);
        } else {
            point.setBlocking(false);
            controlPointRepository.save(point);
        }

        if (result.getStatut() == ResultStatus.NOK && point.isBlocking()) {
            bloquerOF(of.getId());
            if (of.getStatut() == StatutOF.EN_COURS) {
                ofService.mettreEnPause(of.getId());
            }
        } else if (result.getStatut() == ResultStatus.OK) {
            verifierEtDebloquerOF(of.getId());
            OrdreFabrication refreshed = ofRepository.findById(of.getId()).orElseThrow();
            if (refreshed.getQualityStatus() == QualityStatus.FREE && refreshed.getStatut() == StatutOF.EN_PAUSE) {
                ofService.demarrerOF(of.getId());
            }
        }
        return modelMapper.map(result, QCResultDTO.class);
    }

    private void bloquerOF(UUID ofId) {
        OrdreFabrication of = ofRepository.findById(ofId).orElseThrow();
        if (of.getQualityStatus() != QualityStatus.BLOCKED) {
            of.setQualityStatus(QualityStatus.BLOCKED);
            ofRepository.save(of);
            try {
                List<OSMUserDTO> responsables = securityClient.getUsersByRole("OSMADMIN").getBody();
                log.info("Utilisateurs trouvés = {}", responsables.size());
                List<String> userIds = responsables.stream()
                        .map(OSMUserDTO::getOneSignalPlayerId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toList());

                if (!userIds.isEmpty()) {
                    String titre = " OF Bloqué";
                    String message = String.format(
                            "L'OF %s a été bloqué suite à un contrôle qualité non conforme.",
                            of.getCode()
                    );

                    // ✅ FIX 3 : une seule map cohérente
                    Map<String, String> data = Map.of(
                            "screen", "OF_DETAIL",
                            "ofId", ofId.toString()
                    );
                    log.info("Tenant courant : {}", TenantContext.getCurrentTenant());
                    NotificationRequest notif =
                            new NotificationRequest(userIds, titre, message, data);

                    oneSignalService.sendNotification(notif);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'envoi de la notification : " + e.getMessage());
            }
        }
    }

    @Transactional
    public void verifierEtDebloquerOF(UUID ofId) {
        OrdreFabrication of = ofRepository.findById(ofId)
                .orElseThrow(() -> new RuntimeException("OF inconnu"));
        if (of.getQualityStatus() != QualityStatus.BLOCKED) {
            return;
        }
        List<QCResult> allResults = resultRepository.findByOfIdAndTenantIdOrderByDateControleDesc(ofId, TenantContext.getCurrentTenant());
        QCPlan activePlan = qcPlanRepository.findByOfIdAndActifTrue(ofId)
                .orElseThrow(() -> new RuntimeException("Aucun plan actif pour cet OF"));
        List<QCControlPoint> blockingPoints = controlPointRepository.findByPlanIdAndBlockingTrue(activePlan.getId());
        boolean tousControlesOK = true;
        for (QCControlPoint blockingPoint : blockingPoints) {
            boolean pointControle = allResults.stream()
                    .anyMatch(r -> r.getControlPoint().getId().equals(blockingPoint.getId())
                            && r.getStatut() == ResultStatus.OK);

            if (!pointControle) {
                tousControlesOK = false;
                break;
            }
        }
        if (tousControlesOK) {
            of.setQualityStatus(QualityStatus.FREE);
            ofRepository.save(of);
        }
    }

    @Transactional(readOnly = true)
    public List<QCResultDTO> getHistoriqueOF(UUID ofId) {
        return resultRepository.findByOfIdAndTenantIdOrderByDateControleDesc(ofId, TenantContext.getCurrentTenant())
                .stream()
                .map(r -> modelMapper.map(r, QCResultDTO.class))
                .collect(Collectors.toList());
    }
}