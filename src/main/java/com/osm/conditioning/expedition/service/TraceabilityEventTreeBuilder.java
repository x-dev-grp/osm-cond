package com.osm.conditioning.expedition.service;

import com.osm.conditioning.expedition.dto.FiltrationStepDto;
import com.osm.conditioning.expedition.dto.GenealogyDto;
import com.osm.conditioning.expedition.dto.IntakeStepDto;
import com.osm.conditioning.expedition.dto.RootSourceDto;
import com.osm.conditioning.expedition.model.Expedition;
import com.osm.conditioning.expedition.model.ExpeditionArticle;
import com.osm.conditioning.model.OrdreFabrication;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reconstructs a chronological event tree at runtime for project / expedition traceability.
 */
final class TraceabilityEventTreeBuilder     {

    private TraceabilityEventTreeBuilder()  {
    }

    static List<Map<String, Object>> buildChains(
            UUID projectId,
            List<OrdreFabrication> ofs,
            Map<String, GenealogyDto>  genealogyByAnchor,
            Map<String, List<Map<String, Object>>> labelsByAnchor ,
            List<Expedition> expeditions) {

        Map<UUID, List<Expedition>> expeditionsByOfId =  indexExpeditionsByOf(expeditions);

        List<Map<String, Object>> chains = new ArrayList<>();
        for (OrdreFabrication of : ofs) {
            UUID anchor = of.getTraceabilityLotId() != null ? of.getTraceabilityLotId() : of.getLotVracId();
            String anchorKey = anchor != null ? anchor.toString() : null;
            GenealogyDto genealogy =  anchorKey != null ? genealogyByAnchor.get(anchorKey) : null;
            List<Map<String, Object>> labels = anchorKey != null
                    ? labelsByAnchor.getOrDefault(anchorKey, List.of())
                    : List.of();

            Map<String, Object> chain = new  LinkedHashMap<>();
            chain.put("ofId", of.getId() != null ? of.getId().toString() : "");
            chain.put("ofCode", of.getCode());
            chain.put("projectId", projectId != null ? projectId.toString() : "");
            chain.put("traceabilityLotId", of.getTraceabilityLotId() != null ? of.getTraceabilityLotId().toString() : "");
            chain.put("lotVracId", of.getLotVracId() != null ? of.getLotVracId().toString() : "");
            chain.put("genealogyAnchor", anchorKey != null ? anchorKey : "");
            chain.put("events", buildEventsForOf(of, genealogy, labels, expeditionsByOfId.get(of.getId())));
            chains.add(chain);
        }
        return chains;
    }

    private static List<Map<String, Object>> buildEventsForOf(
            OrdreFabrication of,
            GenealogyDto genealogy,
            List<Map<String, Object>> labels,
            List<Expedition> expeditionsForOf) {

        List<Map<String, Object>> events = new ArrayList<>();
        Set<String> seenDeliveryIds = new HashSet<>();
        Set<String> seenTransactionIds = new HashSet<>();
        String lastParentId = null;

        if (genealogy != null) {
            List<FiltrationStepDto> filtrations = sortedFiltrations(genealogy.getFiltrations());

            List<IntakeStepDto> upstreamIntake = resolveUpstreamIntake(genealogy, filtrations);
            if (!upstreamIntake.isEmpty()) {
                lastParentId = appendIntakeSteps(events, upstreamIntake, null, seenDeliveryIds, seenTransactionIds);
            }

            lastParentId = appendRootSources(events, genealogy.getRootSources(), lastParentId, seenDeliveryIds);

            for (FiltrationStepDto step : filtrations) {
                String filtId = eventId("FILTRATION", step.getOperationId());
                events.add(event(
                        filtId,
                        lastParentId,
                        "FILTRATION",
                        phaseLabel("PRODUCTION"),
                        "Filtration → lot " + nullToEmpty(step.getTargetLotNumber()),
                        parseTimestamp(step.getTimestamp()),
                        filtrationDetails(step)));
                if (step.getQualityControls() != null && !step.getQualityControls().isEmpty()) {
                    events.add(event(
                            eventId("FILTRATION_QC", step.getOperationId()),
                            filtId,
                            "FILTRATION_QC",
                            phaseLabel("QUALITY"),
                            "Contrôle qualité filtration",
                            parseTimestamp(step.getTimestamp()),
                            Map.of("qualityControls", step.getQualityControls())));
                }
                lastParentId = filtId;
            }

            lastParentId = appendRemainingIntakeSteps(
                    events,
                    genealogy.getIntakeChain(),
                    lastParentId,
                    seenDeliveryIds,
                    seenTransactionIds);

            if (genealogy.getStorageUnitName() != null || genealogy.getLotNumber() != null) {
                String storageId = eventId("STORAGE", genealogy.getStorageUnitId());
                events.add(event(
                        storageId,
                        lastParentId,
                        "STORAGE",
                        phaseLabel("PRODUCTION"),
                        "Cuve finale : " + nullToEmpty(genealogy.getStorageUnitName()),
                        null,
                        Map.of(
                                "storageUnitId", genealogy.getStorageUnitId(),
                                "storageUnitName", nullToEmpty(genealogy.getStorageUnitName()),
                                "lotNumber", nullToEmpty(genealogy.getLotNumber()),
                                "traceabilityLotId", genealogy.getTraceabilityLotId())));
                lastParentId = storageId;
            }

            if (genealogy.getFilteredQualityControls() != null && !genealogy.getFilteredQualityControls().isEmpty()) {
                events.add(event(
                        eventId("FILTERED_QC", genealogy.getTraceabilityLotId()),
                        lastParentId,
                        "FILTERED_QC",
                        phaseLabel("QUALITY"),
                        "Contrôle qualité lot filtré",
                        null,
                        Map.of("qualityControls", genealogy.getFilteredQualityControls())));
            }
        }

        String ofParent = lastParentId;
        String ofId = eventId("OF", of.getId());
        events.add(event(
                ofId,
                ofParent,
                "OF",
                phaseLabel("CONDITIONING"),
                "Ordre de fabrication " + nullToEmpty(of.getCode()),
                of.getCreatedDate(),
                ofEventDetails(of)));

        List<Map<String, Object>> conditioningTail = new ArrayList<>();
        if (of.getDateDebutReelle() != null) {
            conditioningTail.add(event(
                    eventId("OF_START", of.getId()),
                    ofId,
                    "OF_START",
                    phaseLabel("CONDITIONING"),
                    "Démarrage production OF",
                    of.getDateDebutReelle(),
                    Map.of()));
        }
        if (of.getDateFinReelle() != null) {
            conditioningTail.add(event(
                    eventId("OF_END", of.getId()),
                    ofId,
                    "OF_END",
                    phaseLabel("CONDITIONING"),
                    "Fin production OF",
                    of.getDateFinReelle(),
                    Map.of(
                            "quantityGood", of.getQuantiteBonne(),
                            "quantityTarget", of.getQuantiteCible())));
        }
        for (Map<String, Object> label : labels) {
            Object labelPk = label.get("id");
            conditioningTail.add(event(
                    eventId("LABEL", labelPk),
                    ofId,
                    "LABEL",
                    phaseLabel("CONDITIONING"),
                    "Étiquetage lot " + nullToEmpty((String) label.get("lotNumber")),
                    parseTimestamp(label.get("packagingDate")),
                    label));
        }
        conditioningTail.sort(Comparator.comparing(
                TraceabilityEventTreeBuilder::eventSortKey,
                Comparator.nullsLast(Comparator.naturalOrder())));
        events.addAll(conditioningTail);

        if (expeditionsForOf != null) {
            List<Expedition> sortedExpeditions = new ArrayList<>(expeditionsForOf);
            sortedExpeditions.sort(Comparator.comparing(
                    TraceabilityEventTreeBuilder::expeditionEventTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            for (Expedition expedition : sortedExpeditions) {
                events.add(event(
                        eventId("EXPEDITION", expedition.getId()),
                        ofId,
                        "EXPEDITION",
                        phaseLabel("EXPEDITION"),
                        "Expédition " + nullToEmpty(expedition.getExpeditionNumber()),
                        expeditionEventTimestamp(expedition),
                        expeditionEventDetails(expedition)));
            }
        }

        assignSequences(events);
        return events;
    }

    /**
     * Same rules as the event tree: origin is present if rootSources, rootReceptionId,
     * or an intake step documents réception huile/olive or trituration.
     */
    static boolean hasDocumentedOilOrigin(GenealogyDto genealogy) {
        if (genealogy == null) {
            return false;
        }
        if (genealogy.getRootReceptionId() != null) {
            return true;
        }
        if (genealogy.getRootSources() != null && !genealogy.getRootSources().isEmpty()) {
            return true;
        }
        if (containsOriginIntake(genealogy.getIntakeChain())) {
            return true;
        }
        if (genealogy.getFiltrations() != null) {
            for (FiltrationStepDto step : genealogy.getFiltrations()) {
                if (containsOriginIntake(step.getSourceIntakeChain())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsOriginIntake(List<IntakeStepDto> chain) {
        if (chain == null || chain.isEmpty()) {
            return false;
        }
        for (IntakeStepDto step : chain) {
            if (isOriginIntakeType(step.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOriginIntakeType(String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        return switch (type.toUpperCase()) {
            case "OIL_RECEPTION", "OLIVE_RECEPTION", "RECEPTION", "TRITURATION" -> true;
            default -> false;
        };
    }

    private static List<FiltrationStepDto> sortedFiltrations(List<FiltrationStepDto> filtrations) {
        List<FiltrationStepDto> sorted = new ArrayList<>(filtrations != null ? filtrations : List.of());
        sorted.sort(Comparator.comparing(
                TraceabilityEventTreeBuilder::filtrationSortKey,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    /**
     * Upstream réception + entrée cuve source : from the oldest filtration's source tank, else lot intake chain.
     */
    private static List<IntakeStepDto> resolveUpstreamIntake(GenealogyDto genealogy, List<FiltrationStepDto> filtrationsAsc) {
        if (!filtrationsAsc.isEmpty()) {
            FiltrationStepDto oldest = filtrationsAsc.get(0);
            if (oldest.getSourceIntakeChain() != null && !oldest.getSourceIntakeChain().isEmpty()) {
                return oldest.getSourceIntakeChain();
            }
        }
        return genealogy.getIntakeChain() != null ? genealogy.getIntakeChain() : List.of();
    }

    /** Entrées en cuve sur le lot final (après filtration), sans dupliquer l'amont déjà affiché. */
    private static String appendRemainingIntakeSteps(
            List<Map<String, Object>> events,
            List<IntakeStepDto> fullIntakeChain,
            String parentId,
            Set<String> seenDeliveryIds,
            Set<String> seenTransactionIds) {
        if (fullIntakeChain == null || fullIntakeChain.isEmpty()) {
            return parentId;
        }
        return appendIntakeSteps(events, fullIntakeChain, parentId, seenDeliveryIds, seenTransactionIds);
    }

    private static String appendRootSources(
            List<Map<String, Object>> events,
            List<RootSourceDto> roots,
            String parentId,
            Set<String> seenDeliveryIds) {
        if (roots == null) {
            return parentId;
        }
        String lastParentId = parentId;
        for (RootSourceDto root : roots) {
            if (root.getSourceId() != null && seenDeliveryIds.contains(root.getSourceId().toString())) {
                continue;
            }
            String normalizedType = normalizeRootType(root.getType());
            String rootId = eventId(normalizedType, root.getSourceId());
            events.add(event(
                    rootId,
                    lastParentId,
                    normalizedType,
                    phaseLabel("PRODUCTION"),
                    titleForRoot(root),
                    parseTimestamp(root.getDate()),
                    rootEventDetails(root)));

            if (root.getQualityControls() != null && !root.getQualityControls().isEmpty()) {
                events.add(event(
                        eventId(normalizedType + "_QC", root.getSourceId()),
                        rootId,
                        rootTypeQc(normalizedType),
                        phaseLabel("QUALITY"),
                        "Contrôle qualité réception",
                        parseTimestamp(root.getDate()),
                        Map.of("qualityControls", root.getQualityControls())));
            }
            if (root.getSourceId() != null) {
                seenDeliveryIds.add(root.getSourceId().toString());
            }
            lastParentId = rootId;
        }
        return lastParentId;
    }

    private static String normalizeRootType(String type) {
        if (type == null || type.isBlank()) {
            return "OIL_RECEPTION";
        }
        if ("RECEPTION".equalsIgnoreCase(type)) {
            return "OIL_RECEPTION";
        }
        return type.toUpperCase();
    }

    private static void assignSequences(List<Map<String, Object>> events) {
        String previousEventId = null;
        for (int i = 0; i < events.size(); i++) {
            Map<String, Object> current = events.get(i);
            current.put("sequence", i + 1);
            current.put("parentId", previousEventId);
            previousEventId = String.valueOf(current.get("id"));
        }
    }

    private static Map<UUID, List<Expedition>> indexExpeditionsByOf(List<Expedition> expeditions) {
        Map<UUID, List<Expedition>> byOf = new HashMap<>();
        if (expeditions == null) {
            return byOf;
        }
        for (Expedition expedition : expeditions) {
            if (expedition.getLines() == null) {
                continue;
            }
            for (ExpeditionArticle line : expedition.getLines()) {
                if (line.getOfId() == null) {
                    continue;
                }
                byOf.computeIfAbsent(line.getOfId(), ignored -> new ArrayList<>());
                if (!byOf.get(line.getOfId()).contains(expedition)) {
                    byOf.get(line.getOfId()).add(expedition);
                }
            }
        }
        return byOf;
    }

    private static Map<String, Object> event(
            String id,
            String parentId,
            String type,
            String phase,
            String title,
            LocalDateTime timestamp,
            Map<String, Object> details) {

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("parentId", parentId);
        event.put("type", type);
        event.put("phase", phase);
        event.put("title", title);
        event.put("timestamp", timestamp != null ? timestamp.toString() : null);
        event.put("details", details != null ? details : Map.of());
        return event;
    }

    private static String eventId(String prefix, Object entityId) {
        return prefix + "-" + (entityId != null ? entityId.toString() : "unknown");
    }

    private static String phaseLabel(String phase) {
        return phase;
    }

    private static String appendIntakeSteps(
            List<Map<String, Object>> events,
            List<IntakeStepDto> steps,
            String parentId,
            Set<String> seenDeliveryIds,
            Set<String> seenTransactionIds) {
        String lastId = parentId;
        for (IntakeStepDto intake : steps) {
            String type = intake.getType() != null ? intake.getType() : "INTAKE";
            boolean isStorageIntake = "STORAGE_INTAKE".equals(type);

            if (isStorageIntake && intake.getTransactionId() != null
                    && seenTransactionIds.contains(intake.getTransactionId().toString())) {
                lastId = eventId("STORAGE_INTAKE", intake.getTransactionId());
                continue;
            }

            if (!isStorageIntake && intake.getDeliveryId() != null
                    && seenDeliveryIds.contains(intake.getDeliveryId().toString())) {
                lastId = eventId(type, intake.getDeliveryId());
                continue;
            }

            String nodeId = isStorageIntake
                    ? eventId("STORAGE_INTAKE", intake.getTransactionId())
                    : eventId(type, intake.getDeliveryId());

            events.add(event(
                    nodeId,
                    lastId,
                    type,
                    phaseLabel("PRODUCTION"),
                    titleForIntake(intake),
                    parseTimestamp(intake.getDeliveryDate()),
                    intakeStepDetails(intake)));

            if (intake.getQualityControls() != null && !intake.getQualityControls().isEmpty()) {
                events.add(event(
                        eventId(type + "_QC", intake.getDeliveryId()),
                        nodeId,
                        rootTypeQc(type),
                        phaseLabel("QUALITY"),
                        "Contrôle qualité " + intakePhaseLabel(type),
                        parseTimestamp(intake.getDeliveryDate()),
                        Map.of("qualityControls", intake.getQualityControls())));
            }

            if (isStorageIntake && intake.getTransactionId() != null) {
                seenTransactionIds.add(intake.getTransactionId().toString());
            } else if (intake.getDeliveryId() != null) {
                seenDeliveryIds.add(intake.getDeliveryId().toString());
            }
            lastId = nodeId;
        }
        return lastId;
    }

    private static String titleForIntake(IntakeStepDto intake) {
        return switch (nullToEmpty(intake.getType())) {
            case "OLIVE_RECEPTION" -> "Réception olive — " + nullToEmpty(intake.getSupplierName())
                    + " (lot " + nullToEmpty(intake.getLotNumber()) + ")";
            case "OIL_RECEPTION" -> "Réception huile — " + nullToEmpty(intake.getSupplierName())
                    + " (lot " + nullToEmpty(intake.getLotNumber()) + ")";
            case "STORAGE_INTAKE" -> "Entrée en cuve " + nullToEmpty(intake.getStorageUnitName())
                    + (intake.getQuantityKg() != null ? " — " + intake.getQuantityKg() + " kg" : "");
            default -> "Intake " + nullToEmpty(intake.getType());
        };
    }

    private static String intakePhaseLabel(String type) {
        if ("OLIVE_RECEPTION".equals(type)) {
            return "olive";
        }
        if ("OIL_RECEPTION".equals(type)) {
            return "huile";
        }
        return "";
    }

    private static Map<String, Object> intakeStepDetails(IntakeStepDto intake) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deliveryId", intake.getDeliveryId());
        details.put("transactionId", intake.getTransactionId());
        details.put("deliveryNumber", intake.getDeliveryNumber());
        details.put("lotNumber", intake.getLotNumber());
        details.put("lotOliveNumber", intake.getLotOliveNumber());
        details.put("deliveryType", intake.getDeliveryType());
        details.put("supplierName", intake.getSupplierName());
        details.put("quantityKg", intake.getQuantityKg());
        details.put("storageUnitId", intake.getStorageUnitId());
        details.put("storageUnitName", intake.getStorageUnitName());
        if (intake.getExtra() != null) {
            details.put("extra", intake.getExtra());
        }
        return details;
    }

    private static String rootTypeQc(String rootType) {
        if ("OLIVE_RECEPTION".equals(rootType)) {
            return "OLIVE_RECEPTION_QC";
        }
        return "RECEPTION_QC";
    }

    private static String titleForRoot(RootSourceDto root) {
        String type = normalizeRootType(root.getType());
        if ("OLIVE_RECEPTION".equals(type)) {
            return "Réception olive — " + nullToEmpty(root.getSupplierName());
        }
        if ("TRITURATION".equals(type)) {
            return "Trituration / production interne";
        }
        return "Réception huile — " + nullToEmpty(root.getSupplierName());
    }

    private static Map<String, Object> rootEventDetails(RootSourceDto root) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceId", root.getSourceId());
        details.put("lotNumber", root.getLotNumber());
        details.put("supplierName", root.getSupplierName());
        details.put("type", root.getType());
        if (root.getExtra() != null) {
            details.put("extra", root.getExtra());
        }
        return details;
    }

    private static Map<String, Object> filtrationDetails(FiltrationStepDto step) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operationId", step.getOperationId());
        details.put("sourceLotNumber", step.getSourceLotNumber());
        details.put("targetLotNumber", step.getTargetLotNumber());
        details.put("volumeFiltered", step.getVolumeFiltered());
        details.put("sourceStorageUnitId", step.getSourceStorageUnitId());
        details.put("sourceStorageUnitName", step.getSourceStorageUnitName());
        return details;
    }

    private static Map<String, Object> ofEventDetails(OrdreFabrication of) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", of.getCode());
        details.put("status", of.getStatut() != null ? of.getStatut().name() : null);
        details.put("qualityStatus", of.getQualityStatus() != null ? of.getQualityStatus().name() : null);
        details.put("quantityTarget", of.getQuantiteCible());
        details.put("quantityGood", of.getQuantiteBonne());
        details.put("productId", of.getProductId());
        return details;
    }

    private static Map<String, Object> expeditionEventDetails(Expedition expedition) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("expeditionId", expedition.getId());
        details.put("expeditionNumber", expedition.getExpeditionNumber());
        details.put("status", expedition.getStatus() != null ? expedition.getStatus().name() : null);
        details.put("destination", expedition.getDestination());
        details.put("plannedShipDate", expedition.getPlannedShipDate());
        details.put("validatedAt", expedition.getValidatedAt());
        details.put("shippedAt", expedition.getShippedAt());
        details.put("hasFrozenTraceability", expedition.getTraceabilitySnapshotJson() != null
                && !expedition.getTraceabilitySnapshotJson().isBlank());
        return details;
    }

    private static LocalDateTime expeditionEventTimestamp(Expedition expedition) {
        if (expedition.getValidatedAt() != null) {
            return expedition.getValidatedAt();
        }
        if (expedition.getShippedAt() != null) {
            return expedition.getShippedAt();
        }
        return expedition.getCreatedDate();
    }

    private static LocalDateTime parseTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.LocalDate.parse(text).atStartOfDay();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static LocalDateTime filtrationSortKey(FiltrationStepDto step) {
        return parseTimestamp(step.getTimestamp());
    }

    private static LocalDateTime eventSortKey(Map<String, Object> event) {
        return parseTimestamp(event.get("timestamp"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
