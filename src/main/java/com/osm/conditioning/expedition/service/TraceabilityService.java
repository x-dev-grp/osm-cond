package com.osm.conditioning.expedition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionStorage;
import com.osm.conditioning.dto.ProduitFinalDto;
import com.osm.conditioning.expedition.dto.GenealogyDto;
import com.osm.conditioning.expedition.model.Expedition;
import com.osm.conditioning.expedition.model.ExpeditionArticle;
import com.osm.conditioning.model.LabelContent;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.repository.LabelContentRepository;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.xdev.communicator.models.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TraceabilityService {

    private final clientProductionStorage productionStorageClient;
    private final clientInventaire inventaireClient;
    private final OrdreFabricationRepository ofRepository;
    private final LabelContentRepository labelContentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> getLiveProjectTraceability(UUID projectId) {
        try {
            List<OrdreFabrication> ofs = ofRepository.findAllByProjetIdAndIsDeletedFalse(projectId);
            return buildTraceabilityMap(ofs, null);
        } catch (Exception e) {
            log.error("Failed to get live project traceability", e);
            throw new IllegalStateException("Impossible de charger la traÃ§abilitÃ© en direct du projet", e);
        }
    }

    @Transactional
    public String captureTraceabilitySnapshot(Expedition expedition) {
        try {
            List<OrdreFabrication> ofs = resolveProjectOfs(expedition);
            Map<String, Object> snapshot = buildTraceabilityMap(ofs, expedition);
            
            String json = objectMapper.writeValueAsString(snapshot);
            expedition.setTraceabilitySnapshotJson(json);

            log.info("Traceability snapshot captured for expedition {}", expedition.getId());
            return json;
        } catch (Exception e) {
            log.error("Failed to capture traceability snapshot", e);
            throw new IllegalStateException("Impossible de capturer la tracabilite complete de l'expedition", e);
        }
    }

    private Map<String, Object> buildTraceabilityMap(List<OrdreFabrication> ofs, Expedition expedition) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<UUID, Object> oilGenealogy = new LinkedHashMap<>();
        Map<UUID, Object> ofDetails = new LinkedHashMap<>();
        Map<UUID, Object> packagedLabelsByLot = new LinkedHashMap<>();

        for (OrdreFabrication of : ofs) {
            ensureTraceabilityLotId(of);
            UUID ofId = of.getId();
            Map<String, Object> ofSnapshot = new LinkedHashMap<>();
            ofSnapshot.put("code", valueOrEmpty(of.getCode()));
            ofSnapshot.put("productId", of.getProductId() != null ? of.getProductId().toString() : "");
            
            if (of.getProductId() != null) {
                try {
                    ProduitFinalDto product = inventaireClient.getProduitFinalById(of.getProductId());
                    if (product != null) ofSnapshot.put("articleName", product.getName());
                } catch (Exception e) {
                    log.warn("Could not fetch article name for Product {}", of.getProductId());
                }
            }

            ofSnapshot.put("lotVracId", of.getLotVracId() != null ? of.getLotVracId().toString() : "");
            ofSnapshot.put("traceabilityLotId", of.getTraceabilityLotId() != null ? of.getTraceabilityLotId().toString() : "");
            ofSnapshot.put("status", of.getStatut() != null ? of.getStatut().name() : "");
            ofSnapshot.put("qualityStatus", of.getQualityStatus() != null ? of.getQualityStatus().name() : "");
            ofSnapshot.put("quantityTarget", of.getQuantiteCible());
            ofSnapshot.put("quantityGood", of.getQuantiteBonne());
            ofDetails.put(ofId, ofSnapshot);

            UUID genealogyAnchor = of.getTraceabilityLotId() != null ? of.getTraceabilityLotId() : of.getLotVracId();
            if (genealogyAnchor == null) {
                continue;
            }

            try {
                ApiResponse<GenealogyDto> response = productionStorageClient.getGenealogy(genealogyAnchor);
                if (response != null && response.isSuccess() && response.getData() != null) {
                    oilGenealogy.put(genealogyAnchor, response.getData());
                    packagedLabelsByLot.put(genealogyAnchor, labelSnapshotsForLot(of));
                }
            } catch (Exception e) {
                log.warn("Genealogie huile introuvable ou erreur pour l'ancre {}", genealogyAnchor);
            }
        }

        if (expedition != null) {
            snapshot.put("expedition", expeditionSnapshot(expedition));
        }
        
        snapshot.put("ofDetails", ofDetails);
        snapshot.put("oilGenealogy", oilGenealogy);
        snapshot.put("packagedLabelsByLot", packagedLabelsByLot);
        snapshot.put("capturedAt", java.time.LocalDateTime.now().toString());
        
        return snapshot;
    }

    private Map<String, Object> expeditionSnapshot(Expedition expedition) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", expedition.getId());
        data.put("expeditionNumber", expedition.getExpeditionNumber());
        data.put("projectId", expedition.getProjet() != null ? expedition.getProjet().getId() : null);
        data.put("projectCode", expedition.getProjet() != null ? expedition.getProjet().getCode() : null);
        data.put("destination", expedition.getDestination());
        data.put("plannedShipDate", expedition.getPlannedShipDate());
        data.put("carrierName", expedition.getCarrierName());
        data.put("driverName", expedition.getDriverName());
        data.put("truckNumber", expedition.getTruckNumber());
        data.put("trackingNumber", expedition.getTrackingNumber());
        data.put("incoterm", expedition.getIncoterm());
        return data;
    }

    private List<OrdreFabrication> resolveProjectOfs(Expedition expedition) {
        Map<UUID, OrdreFabrication> ordered = new LinkedHashMap<>();

        if (expedition.getLines() != null) {
            for (ExpeditionArticle line : expedition.getLines()) {
                if (line.getOfId() == null) {
                    continue;
                }
                ofRepository.findById(line.getOfId()).ifPresent(of -> {
                    UUID expeditionProjectId = expedition.getProjet() != null ? expedition.getProjet().getId() : null;
                    UUID ofProjectId = of.getProjet() != null ? of.getProjet().getId() : null;

                    if (expeditionProjectId != null && Objects.equals(ofProjectId, expeditionProjectId)) {
                        ordered.put(of.getId(), of);
                    } else {
                        log.warn("OF {} does not belong to project {}, skipping in traceability", of.getId(), expeditionProjectId);
                    }
                });
            }
        }

        if (expedition.getProjet() != null && expedition.getProjet().getId() != null) {
            for (OrdreFabrication of : ofRepository.findAllByProjetIdAndIsDeletedFalse(expedition.getProjet().getId())) {
                ordered.putIfAbsent(of.getId(), of);
            }
        }

        return new ArrayList<>(ordered.values());
    }

    private List<Map<String, Object>> labelSnapshotsForLot(OrdreFabrication of) {
        UUID traceabilityLotId = of.getTraceabilityLotId();
        UUID lotId = of.getLotVracId();

        List<LabelContent> labels = new ArrayList<>();
        if (traceabilityLotId != null) {
            labels.addAll(labelContentRepository.findAllByTraceabilityLotIdAndIsDeletedFalse(traceabilityLotId));
        }
        if (labels.isEmpty() && lotId != null) {
            labels.addAll(labelContentRepository.findAllByLotIdAndIsDeletedFalse(lotId));
        }
        labels.forEach(this::ensureTraceabilityLotId);

        return labels.stream()
                .collect(Collectors.toMap(LabelContent::getId, label -> label, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(LabelContent::getPackagingDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::labelSnapshot)
                .collect(Collectors.toList());
    }

    private Map<String, Object> labelSnapshot(LabelContent label) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", label.getId());
        data.put("publicCode", label.getQrHex());
        data.put("status", label.getStatus() != null ? label.getStatus().name() : null);
        data.put("category", label.getLabelCategory() != null ? label.getLabelCategory().name() : null);
        data.put("lotId", label.getLotId());
        data.put("traceabilityLotId", label.getTraceabilityLotId());
        data.put("lotNumber", label.getLotNumber());
        data.put("packagingId", label.getPackagingId());
        data.put("packagingDate", label.getPackagingDate());
        data.put("bestBeforeDate", label.getBestBeforeDate());
        data.put("netQuantity", label.getNetQuantity());
        data.put("finalizedAt", label.getFinalizedAt());
        data.put("finalizedBy", label.getFinalizedBy());
        data.put("extractionMethod", label.getExtractionMethod());
        data.put("sensoryProfile", label.getSensoryProfile());
        data.put("legalDenomination", label.getLegalDenomination());
        data.put("certifications", label.getCertifications());
        data.put("marketingClaims", label.getMarketingClaims());
        data.put("sourceSnapshots", label.getSourceSnapshots() == null ? List.of() : label.getSourceSnapshots().stream()
                .filter(Objects::nonNull)
                .map(source -> Map.of(
                        "sourceType", source.getSourceType() != null ? source.getSourceType().name() : "",
                        "sourceId", Optional.ofNullable(source.getSourceId()).map(UUID::toString).orElse(""),
                        "sourceBusinessKey", valueOrEmpty(source.getSourceBusinessKey()),
                        "snapshotJson", valueOrEmpty(source.getSnapshotJson())
                ))
                .collect(Collectors.toList()));
        return data;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void ensureTraceabilityLotId(OrdreFabrication of) {
        if (of == null || of.getTraceabilityLotId() != null || of.getLotVracId() == null) {
            return;
        }

        try {
            ApiResponse<GenealogyDto> response = productionStorageClient.getGenealogy(of.getLotVracId());
            if (response != null && response.isSuccess() && response.getData() != null
                    && response.getData().getTraceabilityLotId() != null) {
                of.setTraceabilityLotId(response.getData().getTraceabilityLotId());
                ofRepository.save(of);
            }
        } catch (Exception e) {
            log.warn("Impossible de retro-renseigner traceabilityLotId pour l'OF {}", of.getId());
        }
    }

    private void ensureTraceabilityLotId(LabelContent label) {
        if (label == null || label.getTraceabilityLotId() != null || label.getLotId() == null) {
            return;
        }

        try {
            ApiResponse<GenealogyDto> response = productionStorageClient.getGenealogy(label.getLotId());
            if (response != null && response.isSuccess() && response.getData() != null
                    && response.getData().getTraceabilityLotId() != null) {
                label.setTraceabilityLotId(response.getData().getTraceabilityLotId());
                labelContentRepository.save(label);
            }
        } catch (Exception e) {
            log.warn("Impossible de retro-renseigner traceabilityLotId pour l'etiquette {}", label.getId());
        }
    }
}
