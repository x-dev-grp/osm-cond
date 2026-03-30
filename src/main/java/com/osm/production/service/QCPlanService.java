package com.osm.production.service;

import com.osm.production.dto.QCControlPointDTO;
import com.osm.production.dto.QCPlanDTO;
import com.osm.production.Enum.ControlType;
import com.osm.production.model.QCControlPoint;
import com.osm.production.model.QCPlan;
import com.osm.production.model.OrdreFabrication;
import com.osm.production.repository.OrdreFabricationRepository;
import com.osm.production.repository.QCControlPointRepository;
import com.osm.production.repository.QCPlanRepository;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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

    @Transactional
    public QCPlanDTO createPlan(UUID ofId, String titre) {
        OrdreFabrication of = ordreFabricationRepository.findById(ofId)
                .orElseThrow(() -> new RuntimeException("Ordre de fabrication non trouvé : " + ofId));

        QCPlan plan = new QCPlan();
        plan.setOf(of);
        plan.setTitre(titre);
        plan.setActif(true);
        plan = planRepository.save(plan);
        return modelMapper.map(plan, QCPlanDTO.class);
    }

    @Transactional
    public QCControlPointDTO addControlPoint(UUID planId, QCControlPointDTO dto) {
        QCPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan introuvable"));
        QCControlPoint point = modelMapper.map(dto, QCControlPoint.class);
        point.setPlan(plan);
        point = pointRepository.save(point);
        return modelMapper.map(point, QCControlPointDTO.class);
    }

    public List<QCControlPointDTO> getActivePointsForOF(UUID ofId) {
        QCPlan plan = planRepository.findByOfIdAndActifTrue(ofId)
                .orElseThrow(() -> new RuntimeException("Aucun plan actif pour cet OF"));
        return plan.getPoints().stream()
                .map(p -> modelMapper.map(p, QCControlPointDTO.class))
                .collect(Collectors.toList());
    }


    public QCPlanDTO findActivePlanByOfId(UUID ofId) {
        QCPlan plan = planRepository.findByOfIdAndActifTrue(ofId)
                .orElseThrow(() -> new RuntimeException("Aucun plan actif pour cet OF"));
        return modelMapper.map(plan, QCPlanDTO.class);


    }


}