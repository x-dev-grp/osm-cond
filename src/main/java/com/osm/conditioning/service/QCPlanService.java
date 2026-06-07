package com.osm.conditioning.service;

import com.osm.conditioning.dto.QCControlPointDTO;
import com.osm.conditioning.dto.QCPlanDTO;
import com.osm.conditioning.model.QCControlPoint;
import com.osm.conditioning.model.QCPlan;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.osm.conditioning.repository.QCControlPointRepository;
import com.osm.conditioning.repository.QCPlanRepository;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QCPlanService extends BaseServiceImpl<QCPlan, QCPlanDTO, QCPlanDTO> {

    private final QCPlanRepository planRepository;
    private final QCControlPointRepository pointRepository;
    private final ModelMapper modelMapper;
    private final OrdreFabricationRepository ordreFabricationRepository;

    public QCPlanService(BaseRepository<QCPlan> repository,
                         QCPlanRepository planRepository,
                         QCControlPointRepository pointRepository,
                         ModelMapper modelMapper, OrdreFabricationRepository ordreFabricationRepository) {
        super(repository, modelMapper);
        this.planRepository = planRepository;
        this.pointRepository = pointRepository;
        this.modelMapper = modelMapper;
        this.ordreFabricationRepository = ordreFabricationRepository;
    }

    private QCPlan getPlanEntityById(UUID planId) {
        return planRepository.findByIdAndIsDeletedFalse(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plan introuvable : " + planId));
    }

    private QCControlPoint getControlPointEntityById(UUID pointId) {
        return pointRepository.findByIdAndIsDeletedFalse(pointId)
                .orElseThrow(() -> new EntityNotFoundException("Point de controle introuvable : " + pointId));
    }

    private OrdreFabrication getOfEntityById(UUID ofId) {
        return ordreFabricationRepository.findByIdAndIsDeletedFalse(ofId)
                .orElseThrow(() -> new EntityNotFoundException("Ordre de fabrication non trouve : " + ofId));
    }

    private QCPlan getActivePlanForOf(UUID ofId) {
        getOfEntityById(ofId);
        return planRepository.findByOfIdAndActifTrueAndIsDeletedFalse(ofId)
                .orElseThrow(() -> new RuntimeException("Aucun plan actif pour cet OF"));
    }

    @Transactional
    public QCPlanDTO createPlan(UUID ofId, String titre) {
        OrdreFabrication of = getOfEntityById(ofId);
        if (planRepository.findByOfIdAndActifTrueAndIsDeletedFalse(ofId).isPresent()) {
            throw new RuntimeException("Un plan actif existe déjà pour cet ordre de fabrication.");
        }

        QCPlan plan = new QCPlan();
        plan.setOf(of);
        plan.setTitre(titre);
        plan.setActif(true);
        plan = planRepository.save(plan);
        return modelMapper.map(plan, QCPlanDTO.class);
    }

    @Transactional
    public QCControlPointDTO addControlPoint(UUID planId, QCControlPointDTO dto) {
        QCPlan plan = getPlanEntityById(planId);
        QCControlPoint point = modelMapper.map(dto, QCControlPoint.class);
        point.setPlan(plan);
        point = pointRepository.save(point);
        return modelMapper.map(point, QCControlPointDTO.class);
    }

    @Transactional(readOnly = true)
    public List<QCControlPointDTO> getPointsForOF(UUID ofId) {
        QCPlan plan = getActivePlanForOf(ofId);
        return plan.getPoints().stream()
                .filter(point -> !Boolean.TRUE.equals(point.getDeleted()))
                .map(p -> modelMapper.map(p, QCControlPointDTO.class))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QCPlanDTO findActivePlanByOfId(UUID ofId) {
        QCPlan plan = getActivePlanForOf(ofId);
        return modelMapper.map(plan, QCPlanDTO.class);
    }

    @Transactional
    public void deleteControlPoint(UUID pointId) {
        QCControlPoint point = getControlPointEntityById(pointId);
        point.setDeleted(true);
        pointRepository.save(point);
    }
}
