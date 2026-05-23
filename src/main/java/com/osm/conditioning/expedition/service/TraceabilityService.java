package com.osm.conditioning.expedition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionStorage;
import com.osm.conditioning.dto.ProduitFinalDto;
import com.osm.conditioning.expedition.dto.GenealogyDto;
import com.osm.conditioning.expedition.model.Expedition;
import com.osm.conditioning.expedition.model.ExpeditionArticle;
import com.osm.conditioning.expedition.repository.ExpeditionRepository;
import com.osm.conditioning.model.LabelContent;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.repository.LabelContentRepository;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.xdev.communicator.models.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

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
    private final ExpeditionRepository expeditionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> getLiveProjectTraceability(UUID projectId) {
        try {
            List<OrdreFabrication> ofs = ofRepository.findAllByProjetIdAndIsDeletedFalse(projectId);
            return buildTraceabilityMap(projectId, ofs, null);
        } catch (Exception e) {
            log.error("Failed to get live project traceability", e);
            throw new IllegalStateException("Impossible de charger la traçabilité en direct du projet", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getExpeditionTraceability(Expedition expedition) {
        UUID projectId = expedition.getProjet() != null ? expedition.getProjet().getId() : null;
        List<OrdreFabrication> ofs = resolveExpeditionOfs(expedition);

        if (expedition.getTraceabilitySnapshotJson() != null
                && !expedition.getTraceabilitySnapshotJson().isBlank()) {
            try {
                Map<String, Object> snapshot = objectMapper.readValue(
                        expedition.getTraceabilitySnapshotJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                refreshRuntimeEventChains(snapshot, projectId, ofs, expedition);
                return snapshot;
            } catch (Exception e) {
                log.warn("Snapshot JSON invalide pour expedition {}, reconstruction live", expedition.getId());
            }
        }
        return buildTraceabilityMap(projectId, ofs, expedition);
    }

    private void refreshRuntimeEventChains(
            Map<String, Object> snapshot,
            UUID projectId,
            List<OrdreFabrication> ofs,
            Expedition expedition) {
        Map<String, GenealogyDto> genealogyByAnchor = readGenealogyMap(snapshot.get("oilGenealogy"));
        Map<String, List<Map<String, Object>>> labelsByAnchor = readLabelsMap(snapshot.get("packagedLabelsByLot"));
        List<Expedition> expeditions = projectId != null
                ? expeditionRepository.findAllByProjetIdAndIsDeletedFalseOrderByCreatedDateDesc(projectId)
                : List.of();

        snapshot.put("eventChains", TraceabilityEventTreeBuilder.buildChains(
                projectId, ofs, genealogyByAnchor, labelsByAnchor, expeditions));
        snapshot.put("live", false);
        if (projectId != null) {
            snapshot.put("projectId", projectId.toString());
        }
        if (expedition != null && !snapshot.containsKey("expedition")) {
            snapshot.put("expedition", expeditionSnapshot(expedition));
        }
    }

    private Map<String, GenealogyDto> readGenealogyMap(Object raw) {
        Map<String, GenealogyDto> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        map.forEach((key, value) -> result.put(
                key.toString(),
                objectMapper.convertValue(value, GenealogyDto.class)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> readLabelsMap(Object raw) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        map.forEach((key, value) -> {
            if (value instanceof List<?> list) {
                List<Map<String, Object>> labels = list.stream()
                        .map(item -> objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {}))
                        .collect(Collectors.toList());
                result.put(key.toString(), labels);
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public void assertTraceabilityComplete(Expedition expedition) {
        List<OrdreFabrication> ofs = resolveExpeditionOfs(expedition);
        if (ofs.isEmpty()) {
            throw new IllegalStateException(
                    "Impossible de valider l'expedition : ajoutez au moins une ligne liee a un ordre de fabrication");
        }

        List<String> issues = new ArrayList<>();
        for (OrdreFabrication of : ofs) {
            ensureTraceabilityLotId(of);
            UUID anchor = of.getTraceabilityLotId() != null ? of.getTraceabilityLotId() : of.getLotVracId();
            if (anchor == null) {
                issues.add("OF " + valueOrEmpty(of.getCode()) + " : aucun lot vrac ou lot de tracabilite");
                continue;
            }

            try {
                ApiResponse<GenealogyDto> response = productionStorageClient.getGenealogy(anchor);
                if (response == null || !response.isSuccess() || response.getData() == null) {
                    issues.add("OF " + valueOrEmpty(of.getCode()) + " : genealogie huile introuvable");
                    continue;
                }
                GenealogyDto genealogy = response.getData();
                if (!TraceabilityEventTreeBuilder.hasDocumentedOilOrigin(genealogy)) {
                    issues.add("OF " + valueOrEmpty(of.getCode()) + " : origine reception ou trituration manquante");
                }
            } catch (Exception e) {
                issues.add("OF " + valueOrEmpty(of.getCode()) + " : erreur lors du chargement de la genealogie");
            }
        }

        if (!issues.isEmpty()) {
            throw new IllegalStateException("Tracabilite incomplete : " + String.join(" ; ", issues));
        }
    }

    @Transactional
    public String captureTraceabilitySnapshot(Expedition expedition) {
        try {
            List<OrdreFabrication> ofs = resolveExpeditionOfs(expedition);
            UUID projectId = expedition.getProjet() != null ? expedition.getProjet().getId() : null;
            Map<String, Object> snapshot = buildTraceabilityMap(projectId, ofs, expedition);
            
            String json = objectMapper.writeValueAsString(snapshot);
            expedition.setTraceabilitySnapshotJson(json);

            log.info("Traceability snapshot captured for expedition {}", expedition.getId());
            return json;
        } catch (Exception e) {
            log.error("Failed to capture traceability snapshot", e);
            throw new IllegalStateException("Impossible de capturer la tracabilite complete de l'expedition", e);
        }
    }

    private Map<String, Object> buildTraceabilityMap(UUID projectId, List<OrdreFabrication> ofs, Expedition expedition) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, GenealogyDto> oilGenealogy = new LinkedHashMap<>();
        Map<String, Object> ofDetails = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> packagedLabelsByLot = new LinkedHashMap<>();

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
            ofDetails.put(ofId.toString(), ofSnapshot);

            UUID genealogyAnchor = of.getTraceabilityLotId() != null ? of.getTraceabilityLotId() : of.getLotVracId();
            if (genealogyAnchor == null) {
                continue;
            }

            String anchorKey = genealogyAnchor.toString();
            try {
                ApiResponse<GenealogyDto> response = productionStorageClient.getGenealogy(genealogyAnchor);
                if (response != null && response.isSuccess() && response.getData() != null) {
                    oilGenealogy.put(anchorKey, response.getData());
                    packagedLabelsByLot.put(anchorKey, labelSnapshotsForLot(of));
                }
            } catch (Exception e) {
                log.warn("Genealogie huile introuvable ou erreur pour l'ancre {}", genealogyAnchor);
            }
        }

        List<Expedition> projectExpeditions = projectId != null
                ? expeditionRepository.findAllByProjetIdAndIsDeletedFalseOrderByCreatedDateDesc(projectId)
                : List.of();

        if (projectId != null) {
            snapshot.put("projectId", projectId.toString());
        }
        if (expedition != null) {
            snapshot.put("expedition", expeditionSnapshot(expedition));
        }

        snapshot.put("ofDetails", ofDetails);
        snapshot.put("oilGenealogy", oilGenealogy);
        snapshot.put("packagedLabelsByLot", packagedLabelsByLot);
        snapshot.put("eventChains", TraceabilityEventTreeBuilder.buildChains(
                projectId, ofs, oilGenealogy, packagedLabelsByLot, projectExpeditions));
        snapshot.put("capturedAt", java.time.LocalDateTime.now().toString());
        snapshot.put("live", expedition == null || expedition.getTraceabilitySnapshotJson() == null);

        return snapshot;
    }

    private Map<String, Object> expeditionSnapshot(Expedition expedition) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", expedition.getId());
        data.put("expeditionNumber", expedition.getExpeditionNumber());
        data.put("projectId", expedition.getProjet() != null ? expedition.getProjet().getId() : null);
        data.put("projectCode", expedition.getProjet() != null ? expedition.getProjet().getCode() : null);
        if (expedition.getProjet() != null && expedition.getProjet().getClient() != null) {
            data.put("clientName", expedition.getProjet().getClient().getNom());
        }
        data.put("destination", expedition.getDestination());
        data.put("plannedShipDate", expedition.getPlannedShipDate());
        data.put("carrierName", expedition.getCarrierName());
        data.put("driverName", expedition.getDriverName());
        data.put("truckNumber", expedition.getTruckNumber());
        data.put("trackingNumber", expedition.getTrackingNumber());
        data.put("incoterm", expedition.getIncoterm());
        return data;
    }

    private List<OrdreFabrication> resolveExpeditionOfs(Expedition expedition) {
        Map<UUID, OrdreFabrication> ordered = new LinkedHashMap<>();
        UUID expeditionProjectId = expedition.getProjet() != null ? expedition.getProjet().getId() : null;

        if (expedition.getLines() == null || expedition.getLines().isEmpty()) {
            return List.of();
        }

        for (ExpeditionArticle line : expedition.getLines()) {
            if (line.getOfId() == null) {
                continue;
            }
            ofRepository.findById(line.getOfId()).ifPresent(of -> {
                UUID ofProjectId = of.getProjet() != null ? of.getProjet().getId() : null;

                if (expeditionProjectId != null && Objects.equals(ofProjectId, expeditionProjectId)) {
                    ordered.put(of.getId(), of);
                } else {
                    log.warn("OF {} does not belong to project {}, skipping in traceability", of.getId(), expeditionProjectId);
                }
            });
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
