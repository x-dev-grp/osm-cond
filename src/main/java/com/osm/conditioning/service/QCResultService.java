package com.osm.conditioning.service;





import com.osm.conditioning.Enum.StatutOF;

import com.osm.conditioning.client.SecurityClient;

import com.osm.conditioning.dto.AssignableUserDTO;

import com.osm.conditioning.dto.QCResultDTO;

import com.osm.conditioning.Enum.ControlType;

import com.osm.conditioning.Enum.QualityStatus;

import com.osm.conditioning.Enum.ResultStatus;

import com.osm.conditioning.model.*;

import com.osm.conditioning.repository.*;

import com.xdev.onsignalNotifcations.dto.NotificationRequest;

import com.xdev.onsignalNotifcations.impl.OneSignalServiceImpl;

import com.xdev.xdevbase.config.TenantContext;

import com.xdev.xdevbase.repos.BaseRepository;

import com.xdev.xdevbase.services.impl.BaseServiceImpl;

import jakarta.persistence.EntityNotFoundException;

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



    private OrdreFabrication getOfEntityById(UUID ofId) {

        return ofRepository.findByIdAndIsDeletedFalse(ofId)

                .orElseThrow(() -> new EntityNotFoundException("OF inconnu : " + ofId));

    }



    @Transactional

    public QCResultDTO enregistrerResultat(QCResultDTO dto) {

        QCControlPoint point = controlPointRepository.findByIdAndIsDeletedFalse(dto.getControlPointId())

                .orElseThrow(() -> new RuntimeException("Point de contrôle inconnu"));

        OrdreFabrication of = getOfEntityById(dto.getOfId());



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

            OrdreFabrication refreshed = getOfEntityById(of.getId());

            if (refreshed.getQualityStatus() == QualityStatus.FREE && refreshed.getStatut() == StatutOF.EN_PAUSE) {

                ofService.demarrerOF(of.getId());

            }

        }

        return modelMapper.map(result, QCResultDTO.class);

    }



    private void bloquerOF(UUID ofId) {

        OrdreFabrication of = getOfEntityById(ofId);

        if (of.getQualityStatus() != QualityStatus.BLOCKED) {

            of.setQualityStatus(QualityStatus.BLOCKED);

            ofRepository.save(of);

            try {

                List<AssignableUserDTO> responsables = securityClient

                        .getUsersByPermission("CONDITIONING", "OF", "READ")

                        .getBody();

                if (responsables == null) {

                    responsables = List.of();

                }

                log.info("Utilisateurs trouvés = {}", responsables.size());

                List<String> userIds = responsables.stream()

                        .map(AssignableUserDTO::getOneSignalPlayerId)

                        .filter(id -> id != null && !id.isBlank())

                        .distinct()

                        .collect(Collectors.toList());



                if (!userIds.isEmpty()) {

                    String titre = " OF Bloqué";

                    String message = String.format(

                            "L'OF %s a été bloqué suite à un contrôle qualité non conforme.",

                            of.getCode()

                    );



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

                log.warn("Erreur lors de l'envoi de la notification OF bloquee", e);

            }

        }

    }



    @Transactional

    public void verifierEtDebloquerOF(UUID ofId) {

        OrdreFabrication of = getOfEntityById(ofId);

        if (of.getQualityStatus() != QualityStatus.BLOCKED) {

            return;

        }

        List<QCResult> allResults = resultRepository.findByOfIdAndTenantIdOrderByDateControleDesc(ofId, TenantContext.getCurrentTenant());

        QCPlan activePlan = qcPlanRepository.findByOfIdAndActifTrueAndIsDeletedFalse(ofId)

                .orElseThrow(() -> new RuntimeException("Aucun plan actif pour cet OF"));

        List<QCControlPoint> blockingPoints = controlPointRepository.findByPlanIdAndBlockingTrueAndIsDeletedFalse(activePlan.getId());

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

        getOfEntityById(ofId);

        return resultRepository.findByOfIdAndTenantIdOrderByDateControleDesc(ofId, TenantContext.getCurrentTenant())

                .stream()

                .map(r -> modelMapper.map(r, QCResultDTO.class))

                .collect(Collectors.toList());

    }

}

