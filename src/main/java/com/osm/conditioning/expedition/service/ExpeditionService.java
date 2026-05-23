package com.osm.conditioning.expedition.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.dto.ArticleSecDto;
import com.osm.conditioning.dto.ProduitFinalDto;
import com.osm.conditioning.dto.StockSecDto;
import com.osm.conditioning.expedition.dto.*;
import com.osm.conditioning.expedition.enums.ExpeditionStatus;
import com.osm.conditioning.expedition.model.Expedition;
import com.osm.conditioning.expedition.model.ExpeditionArticle;
import com.osm.conditioning.expedition.repository.ExpeditionArticleRepository;
import com.osm.conditioning.expedition.repository.ExpeditionRepository;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.repository.ProjetRepository;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.model.QrCodeInfo;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.models.Action;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpeditionService extends BaseServiceImpl<Expedition, ExpeditionDto, ExpeditionDto> {

    private final ExpeditionRepository expeditionRepository;
    private final ExpeditionArticleRepository expeditionArticleRepository;
    private final ProjetRepository projetRepository;
    private final clientInventaire inventaireClient;
    private final OrdreFabricationRepository ofRepository;
    private final TraceabilityService traceabilityService;

    public ExpeditionService(
            BaseRepository<Expedition> repository,
            CodeGenerator codeGenerator,
            ModelMapper modelMapper,
            ExpeditionRepository expeditionRepository,
            ExpeditionArticleRepository expeditionArticleRepository,
            ProjetRepository projetRepository,
            clientInventaire inventaireClient, OrdreFabricationRepository ofRepository,
            TraceabilityService traceabilityService
    ) {
        super(repository, codeGenerator, modelMapper);
        this.expeditionRepository = expeditionRepository;
        this.expeditionArticleRepository = expeditionArticleRepository;
        this.projetRepository = projetRepository;
        this.inventaireClient = inventaireClient;
        this.ofRepository = ofRepository;
        this.traceabilityService = traceabilityService;
    }

    /* ──────────────────────── READ ──────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public ExpeditionDto findById(UUID id) {
        return getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpeditionDto> findAll() {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<Expedition> expeditions = tenantId == null
                ? expeditionRepository.findAllByIsDeletedFalseOrderByCreatedDateDesc()
                : expeditionRepository.findAllByTenantIdAndIsDeletedFalseOrderByCreatedDateDesc(tenantId);

        return expeditions
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpeditionDto getById(UUID expeditionId) {
        Expedition expedition = findExpedition(expeditionId);
        return toDto(expedition);
    }

    @Transactional(readOnly = true)
    public List<ExpeditionDto> getByProject(UUID projectId) {
        return expeditionRepository.findAllByProjetIdAndIsDeletedFalseOrderByCreatedDateDesc(projectId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    private byte[] generateSimpleQrImage(String content) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    300,
                    300,
                    hints
            );

            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate expedition QR image", e);
        }
    }
    /* ──────────────────────── CREATE / UPDATE ──────────────────────── */
    @Override
    protected byte[] generateQrImageBytesFromEntity(Expedition expedition) {
        if (expedition == null || expedition.getQrHex() == null || expedition.getQrHex().isBlank()) {
            throw new IllegalArgumentException("Expedition QR code is missing");
        }

        String qrUrl = getQrUrlForPublicCode(expedition.getQrHex());

        return generateSimpleQrImage(qrUrl);
    }
    @Transactional
    public ExpeditionDto create(ExpeditionCreationRequest request) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(request.getProjetId())
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + request.getProjetId()));

        if (projet.getClient() == null || projet.getClient().getId() == null) {
            throw new IllegalStateException("Le projet n'a pas de client exploitable pour l'expedition");
        }

        Expedition expedition = new Expedition();
        expedition.setExpeditionNumber(generateExpeditionNumber());
        expedition.setProjet(projet);
        expedition.setClientId(projet.getClient().getId());
        expedition.setStatus(ExpeditionStatus.DRAFT);

        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            expedition.setTenantId(tenantId);
        }

        applyUpdatableFields(
                expedition,
                request.getDestination(),
                request.getPlannedShipDate(),
                request.getNotes(),
                null,
                null,
                null,
                null,
                null
        );

        if (request.getLines() != null) {
            for (ExpeditionLineCreateRequest lineRequest : request.getLines()) {
                appendLine(expedition, lineRequest);
            }
        }

        Expedition saved = expeditionRepository.save(expedition);

        if (saved.getQrHex() == null || saved.getQrHex().isBlank()) {
            QrCodeInfo qrInfo = generateQrInfo(getEntityType(), saved.getId());

            saved.setQrHex(qrInfo.getPublicCode());
            saved.setQrImageBase64(qrInfo.getQrImageBase64());

            saved = expeditionRepository.save(saved);
        }

        return toDto(saved);
    }
    @Override
    protected Object getQrPayload(Expedition expedition) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("entityType", getEntityType());
        payload.put("id", expedition.getId());
        payload.put("publicCode", expedition.getQrHex());
        payload.put("expeditionNumber", expedition.getExpeditionNumber());
        payload.put("status", expedition.getStatus() != null ? expedition.getStatus().name() : null);
        payload.put("clientId", getExpeditionClientId(expedition));

        if (expedition.getProjet() != null) {
            payload.put("projetId", expedition.getProjet().getId());
            payload.put("projetCode", expedition.getProjet().getCode());
        }

        return payload;
    }
    @Transactional
    public ExpeditionDto update(UUID expeditionId, ExpeditionUpdateRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        ensureEditable(expedition.getStatus());

        applyUpdatableFields(expedition, request.getDestination(), request.getPlannedShipDate(), request.getNotes(),
                request.getCarrierName(), request.getDriverName(), request.getTruckNumber(),
                request.getTrackingNumber(), request.getIncoterm());

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    /* ──────────────────────── LINES ──────────────────────── */

    @Transactional
    public ExpeditionDto addLine(UUID expeditionId, ExpeditionLineCreateRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        ensureEditable(expedition.getStatus());

        appendLine(expedition, request);
        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto removeLine(UUID expeditionId, UUID lineId) {
        Expedition expedition = findExpedition(expeditionId);
        ensureEditable(expedition.getStatus());

        ExpeditionArticle line = expeditionArticleRepository.findByIdAndExpeditionIdAndIsDeletedFalse(lineId, expeditionId)
                .orElseThrow(() -> new EntityNotFoundException("Ligne d'expedition introuvable : " + lineId));

        expedition.getLines().removeIf(existing -> line.getId().equals(existing.getId()));
        expeditionArticleRepository.delete(line);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    /* ──────────────────────── STATUS TRANSITIONS ──────────────────────── */
    /*
     *  DRAFT  →  READY  →  VALIDATED  →  SHIPPED  →  DELIVERED  →  CLOSED
     *    └──────────┴───────────┘ → CANCELLED
     */

    @Transactional
    public ExpeditionDto markReady(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        if (expedition.getStatus() != ExpeditionStatus.DRAFT) {
            throw new IllegalStateException("Seule une expedition DRAFT peut passer en READY");
        }
        if (expedition.getLines().isEmpty()) {
            throw new IllegalStateException("Impossible de passer READY sans lignes");
        }

        // Validate cumulative stock for all lines
        validateExpeditionStock(expedition);

        expedition.setStatus(ExpeditionStatus.READY);
        appendActionComment(expedition, "READY", request);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto validate(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        if (expedition.getStatus() != ExpeditionStatus.READY) {
            throw new IllegalStateException("La validation exige une expedition READY");
        }

        expedition.setStatus(ExpeditionStatus.VALIDATED);
        expedition.setValidatedAt(LocalDateTime.now());
        appendActionComment(expedition, "VALIDATED", request);

        // Capture irreversible traceability snapshot
        traceabilityService.captureTraceabilitySnapshot(expedition);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto ship(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        if (expedition.getStatus() != ExpeditionStatus.VALIDATED) {
            throw new IllegalStateException("Le shipping exige une expedition VALIDATED");
        }

        expedition.setStatus(ExpeditionStatus.SHIPPED);
        expedition.setShippedAt(LocalDateTime.now());
        appendActionComment(expedition, "SHIPPED", request);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto deliver(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        if (expedition.getStatus() != ExpeditionStatus.SHIPPED) {
            throw new IllegalStateException("La livraison exige une expedition SHIPPED");
        }

        expedition.setStatus(ExpeditionStatus.DELIVERED);
        expedition.setDeliveredAt(LocalDateTime.now());
        appendActionComment(expedition, "DELIVERED", request);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto close(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);
        if (expedition.getStatus() != ExpeditionStatus.SHIPPED && expedition.getStatus() != ExpeditionStatus.DELIVERED) {
            throw new IllegalStateException("La cloture exige une expedition SHIPPED ou DELIVERED");
        }

        expedition.setStatus(ExpeditionStatus.CLOSED);
        expedition.setClosedAt(LocalDateTime.now());
        appendActionComment(expedition, "CLOSED", request);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional
    public ExpeditionDto cancel(UUID expeditionId, ExpeditionActionRequest request) {
        Expedition expedition = findExpedition(expeditionId);

        if (expedition.getStatus() == ExpeditionStatus.SHIPPED
                || expedition.getStatus() == ExpeditionStatus.DELIVERED
                || expedition.getStatus() == ExpeditionStatus.CLOSED) {
            throw new IllegalStateException("Impossible d'annuler une expedition SHIPPED/DELIVERED/CLOSED");
        }

        if (expedition.getStatus() == ExpeditionStatus.CANCELLED) {
            return toDto(expedition);
        }

        expedition.setStatus(ExpeditionStatus.CANCELLED);
        expedition.setCancelledAt(LocalDateTime.now());
        appendActionComment(expedition, "CANCELLED", request);

        Expedition saved = expeditionRepository.save(expedition);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProjectTraceability(UUID projectId) {
        return traceabilityService.getLiveProjectTraceability(projectId);
    }

    /* ──────────────────────── QR / RESOLVE ──────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        if (publicCode == null || publicCode.isBlank()) {
            throw new IllegalArgumentException("Le code expedition est obligatoire");
        }

        String normalized = publicCode.trim().toUpperCase(Locale.ROOT);

        Optional<Expedition> match = expeditionRepository.findByQrHexAndIsDeletedFalse(normalized);
        if (match.isEmpty()) {
            match = expeditionRepository.findByExpeditionNumberIgnoreCaseAndIsDeletedFalse(normalized);
        }

        Expedition expedition = match.orElseThrow(() ->
                new EntityNotFoundException("Expedition introuvable pour le code : " + publicCode));

        QrResolveResponse response = new QrResolveResponse();
        response.setEntityType(getEntityType());
        response.setPublicCode(normalized);
        response.setEntityId(expedition.getId().toString());
        response.setLabel(expedition.getExpeditionNumber());
        response.setStatus(expedition.getStatus() != null ? expedition.getStatus().name() : null);
        response.setMobileRoute(getMobileRoute());
        response.setWebRoute(getWebRoute(expedition));
        response.setData(toDto(expedition));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrResolveResponse> searchByCode(String code) {
        try {
            return Optional.ofNullable(resolve(code));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public byte[] generateQrImageFromEntity(Expedition entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("Expedition invalide pour generation QR");
        }

        if (entity.getQrHex() != null && !entity.getQrHex().isBlank()) {
            return generateQrImage(entity.getQrHex());
        }

        QrCodeInfo qrInfo = generateQrInfo(getEntityType(), entity.getId());
        return generateQrImage(qrInfo.getPublicCode());
    }

    /* ──────────────────────── OVERRIDES ──────────────────────── */

    @Override
    protected String getEntityType() {
        return "EXPEDITION";
    }

    @Override
    protected String getLabel(Expedition entity) {
        return entity.getExpeditionNumber();
    }

    @Override
    protected String getStatus(Expedition entity) {
        return entity.getStatus() != null ? entity.getStatus().name() : null;
    }

    @Override
    protected String getMobileRoute() {
        return "/projets/detail";
    }

    @Override
    protected String getWebRoute(Expedition entity) {
        if (entity == null || entity.getProjet() == null || entity.getProjet().getId() == null) {
            return "/projets";
        }
        return "/projets/detail/" + entity.getProjet().getId() + "/expedition";
    }

    /* ──────────────────────── PRIVATE HELPERS ──────────────────────── */

    private void validateOfBelongsToProject(UUID ofId, UUID projectId) {
        OrdreFabrication of = ofRepository.findById(ofId).orElseThrow(() -> new EntityNotFoundException("Ordre de fabrication introuvable : " + ofId));

        if (of.getProjet() == null || of.getProjet().getId() == null) {
            throw new IllegalArgumentException("L'ordre de fabrication n'est rattache a aucun projet");
        }

        if (!Objects.equals(of.getProjet().getId(), projectId)) {
            throw new IllegalArgumentException("L'ordre de fabrication n'appartient pas au projet de l'expedition");
        }
    }

    private void validateExpeditionStock(Expedition expedition) {
        Map<UUID, Integer> articleQuantities = new HashMap<>();
        for (ExpeditionArticle line : expedition.getLines()) {
            if (line.getArticleId() != null) {
                articleQuantities.merge(line.getArticleId(), line.getQuantity(), Integer::sum);
            }
        }

        for (Map.Entry<UUID, Integer> entry : articleQuantities.entrySet()) {
            UUID articleId = entry.getKey();
            Integer requiredQuantity = entry.getValue();

            StockSecDto stock = inventaireClient.getStockByArticle(articleId);
            Integer available = null;
            if (stock != null) {
                available = stock.getQuantiteDisponible() != null
                        ? stock.getQuantiteDisponible()
                        : stock.getQuantiteActuelle();
            }
            if (available == null || available < requiredQuantity) {
                throw new IllegalStateException("Stock insuffisant pour l'article " + articleId + " : disponible " + available + ", requis " + requiredQuantity);
            }
        }
    }

    private void appendLine(Expedition expedition, ExpeditionLineCreateRequest request) {
        Integer quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        String lotNumber = normalizeNullable(request.getLotNumber());
        String unit = resolveUnit(request.getUnit());

        OrdreFabrication of;
        if (request.getOfId() != null) {
            of = assignOrValidateOfProject(request.getOfId(), expedition.getProjet());
            if (of.getStatut() != StatutOF.CLOTURE) {
                throw new IllegalArgumentException("Seul un OF cloture peut etre expedie");
            }

            if ((quantity == null || quantity <= 0) && of.getQuantiteBonne() != null && of.getQuantiteBonne().signum() > 0) {
                quantity = of.getQuantiteBonne().intValue();
            }
            if ((quantity == null || quantity <= 0) && of.getQuantiteCible() != null && of.getQuantiteCible().signum() > 0) {
                quantity = of.getQuantiteCible().intValue();
            }
            if (lotNumber == null && of.getLotVracId() != null) {
                lotNumber = of.getLotVracId().toString();
            }

            int alreadyAllocated = sumAllocatedQuantityForOf(of.getId(), expedition.getId());
            int currentExpeditionQty = expedition.getLines().stream()
                    .filter(l -> l.getOfId() != null && l.getOfId().equals(of.getId()))
                    .map(ExpeditionArticle::getQuantity)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            int maxShippable = of.getQuantiteBonne() != null ? of.getQuantiteBonne().intValue() : 0;
            int requested = quantity == null ? 0 : quantity;
            int projected = alreadyAllocated + currentExpeditionQty + requested;
            if (projected > maxShippable) {
                throw new IllegalArgumentException(
                        "Quantite expedition depasse la quantite conforme de l'OF (max " + maxShippable + ", deja allouee " + (alreadyAllocated + currentExpeditionQty) + ")"
                );
            }
        } else {
            of = null;
        }

        UUID articleId = request.getArticleId();
        if (articleId == null && of != null && of.getLignes() != null && of.getLignes().size() == 1) {
            articleId = of.getLignes().get(0).getArticleId();
        }

        ArticleSecDto article = null;
        if (articleId != null) {
            article = inventaireClient.getArticleById(articleId);
            if (article == null) {
                throw new IllegalArgumentException("Article introuvable : " + articleId);
            }
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("La quantite doit etre superieure a 0");
        }

        ExpeditionArticle line = new ExpeditionArticle();
        line.setExpedition(expedition);
        line.setOfId(request.getOfId());
        line.setArticleId(articleId);
        line.setOfCode(of != null ? of.getCode() : null);

        if (article != null) {
            line.setArticleNameSnapshot(article.getNom());
        } else if (of != null && of.getProductId() != null) {
            try {
                ProduitFinalDto product = inventaireClient.getProduitFinalById(of.getProductId());
                if (product != null && product.getName() != null) {
                    line.setArticleNameSnapshot(product.getName());
                }
            } catch (Exception ignored) {
                if (line.getArticleNameSnapshot() == null) {
                    line.setArticleNameSnapshot(of.getCode());
                }
            }
        }

        line.setQuantity(quantity);
        line.setVolume(request.getVolume());
        line.setLotNumber(lotNumber);
        line.setUnit(unit);

        expedition.getLines().add(line);
    }

    private OrdreFabrication assignOrValidateOfProject(UUID ofId, Projet project) {
        OrdreFabrication of = ofRepository.findById(ofId)
                .orElseThrow(() -> new EntityNotFoundException("Ordre de fabrication introuvable : " + ofId));

        if (project == null || project.getId() == null) {
            throw new IllegalArgumentException("Projet expedition invalide");
        }

        if (of.getProjet() == null || of.getProjet().getId() == null) {
            of.setProjet(project);
            return ofRepository.save(of);
        }

        if (!Objects.equals(of.getProjet().getId(), project.getId())) {
            throw new IllegalArgumentException("L'ordre de fabrication n'appartient pas au projet de l'expedition");
        }

        return of;
    }

    private Expedition findExpedition(UUID expeditionId) {
        return expeditionRepository.findByIdAndIsDeletedFalse(expeditionId)
                .orElseThrow(() -> new EntityNotFoundException("Expedition introuvable : " + expeditionId));
    }

    private int sumAllocatedQuantityForOf(UUID ofId, UUID currentExpeditionId) {
        if (ofId == null) {
            return 0;
        }

        return expeditionRepository.findAllByIsDeletedFalseOrderByCreatedDateDesc().stream()
                .filter(exp -> currentExpeditionId == null || !exp.getId().equals(currentExpeditionId))
                .filter(exp -> exp.getStatus() != ExpeditionStatus.CANCELLED)
                .flatMap(exp -> exp.getLines().stream())
                .filter(line -> ofId.equals(line.getOfId()))
                .map(ExpeditionArticle::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void ensureEditable(ExpeditionStatus status) {
        if (status == ExpeditionStatus.VALIDATED
                || status == ExpeditionStatus.SHIPPED
                || status == ExpeditionStatus.DELIVERED
                || status == ExpeditionStatus.CLOSED
                || status == ExpeditionStatus.CANCELLED) {
            throw new IllegalStateException("Modification interdite apres VALIDATED");
        }
    }

    private void applyUpdatableFields(Expedition expedition,
                                       String destination, LocalDate plannedShipDate, String notes,
                                       String carrierName, String driverName, String truckNumber,
                                       String trackingNumber, String incoterm) {
        expedition.setDestination(normalizeNullable(destination));
        expedition.setPlannedShipDate(plannedShipDate);
        expedition.setNotes(normalizeNullable(notes));

        // Transport fields â€” always updatable while editable
        if (carrierName != null) expedition.setCarrierName(normalizeNullable(carrierName));
        if (driverName != null) expedition.setDriverName(normalizeNullable(driverName));
        if (truckNumber != null) expedition.setTruckNumber(normalizeNullable(truckNumber));
        if (trackingNumber != null) expedition.setTrackingNumber(normalizeNullable(trackingNumber));
        if (incoterm != null) expedition.setIncoterm(normalizeNullable(incoterm));
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "UNIT";
        }
        return unit.trim().toUpperCase(Locale.ROOT);
    }

    private void appendActionComment(Expedition expedition, String step, ExpeditionActionRequest request) {
        if (request == null || request.getComment() == null || request.getComment().isBlank()) {
            return;
        }

        String currentNotes = expedition.getNotes();
        String actionLine = "[" + step + "] " + request.getComment().trim();

        if (currentNotes == null || currentNotes.isBlank()) {
            expedition.setNotes(actionLine);
            return;
        }

        expedition.setNotes(currentNotes + "\n" + actionLine);
    }

    private ExpeditionDto toDto(Expedition expedition) {
        ExpeditionDto dto = new ExpeditionDto();
        dto.setId(expedition.getId());
        dto.setExpeditionNumber(expedition.getExpeditionNumber());
        dto.setClientId(getExpeditionClientId(expedition));
        dto.setStatus(expedition.getStatus());
        dto.setDestination(expedition.getDestination());
        dto.setPlannedShipDate(expedition.getPlannedShipDate());
        dto.setValidatedAt(expedition.getValidatedAt());
        dto.setShippedAt(expedition.getShippedAt());
        dto.setDeliveredAt(expedition.getDeliveredAt());
        dto.setClosedAt(expedition.getClosedAt());
        dto.setCancelledAt(expedition.getCancelledAt());
        dto.setNotes(expedition.getNotes());
        dto.setCreatedDate(expedition.getCreatedDate());
        dto.setLastModifiedDate(expedition.getLastModifiedDate());
        dto.setPublicCode(expedition.getQrHex());
        dto.setQrImageBase64(expedition.getQrImageBase64());
        dto.setTraceabilitySnapshotJson(expedition.getTraceabilitySnapshotJson());

        // Transport fields
        dto.setCarrierName(expedition.getCarrierName());
        dto.setDriverName(expedition.getDriverName());
        dto.setTruckNumber(expedition.getTruckNumber());
        dto.setTrackingNumber(expedition.getTrackingNumber());
        dto.setIncoterm(expedition.getIncoterm());

        if (expedition.getProjet() != null) {
            dto.setProjetId(expedition.getProjet().getId());
            dto.setProjetCode(expedition.getProjet().getCode());
        }

        List<ExpeditionArticle> orderedLines = new ArrayList<>(expedition.getLines());
        orderedLines.sort(Comparator.comparing(ExpeditionArticle::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())));

        List<ExpeditionArticleDto> lineDtos = orderedLines.stream()
                .map(this::toLineDto)
                .collect(Collectors.toList());
        dto.setLines(lineDtos);

        int totalQuantity = orderedLines.stream()
                .map(ExpeditionArticle::getQuantity)
                .filter(q -> q != null)
                .mapToInt(Integer::intValue)
                .sum();

        BigDecimal totalVolume = orderedLines.stream()
                .map(ExpeditionArticle::getVolume)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setTotalQuantity(totalQuantity);
        dto.setTotalVolume(totalVolume);

        return dto;
    }

    private UUID getExpeditionClientId(Expedition expedition) {
        if (expedition == null) {
            return null;
        }

        if (expedition.getClientId() != null) {
            return expedition.getClientId();
        }

        if (expedition.getProjet() != null && expedition.getProjet().getClient() != null) {
            return expedition.getProjet().getClient().getId();
        }

        return null;
    }

    private ExpeditionArticleDto toLineDto(ExpeditionArticle line) {
        ExpeditionArticleDto dto = new ExpeditionArticleDto();
        dto.setId(line.getId());
        dto.setOfId(line.getOfId());
        dto.setOfCode(line.getOfCode());
        dto.setArticleId(line.getArticleId());
        dto.setArticleName(line.getArticleNameSnapshot());
        dto.setQuantity(line.getQuantity());
        dto.setVolume(line.getVolume());
        dto.setLotNumber(line.getLotNumber());
        dto.setUnit(line.getUnit());
        return dto;
    }

    private String generateExpeditionNumber() {
        return generateBusinessCode("expeditionNumber", "EX");
    }

    @Override
    public Set<Action> actionsMapping(Expedition expedition) {
        return Set.of(
                Action.READ,
                Action.CREATE,
                Action.UPDATE,
                Action.DELETE,
                Action.ADD_LINE,
                Action.REMOVE_LINE,
                Action.VALIDATE,
                Action.SHIP,
                Action.DELIVER,
                Action.CLOSE,
                Action.CANCEL,
                Action.GEN_PDF
        );
    }
}
