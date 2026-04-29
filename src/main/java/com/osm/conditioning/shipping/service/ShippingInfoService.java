package com.osm.conditioning.shipping.service;

import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.dto.ArticleSecDto;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.repository.ProjetRepository;
import com.osm.conditioning.shipping.dto.*;
import com.osm.conditioning.shipping.enums.ShippingEventType;
import com.osm.conditioning.shipping.enums.ShippingStatus;
import com.osm.conditioning.shipping.model.ShippingEvent;
import com.osm.conditioning.shipping.model.ShippingInfo;
import com.osm.conditioning.shipping.model.ShippingLine;
import com.osm.conditioning.shipping.repository.ShippingInfoRepository;
import com.osm.conditioning.shipping.repository.ShippingLineRepository;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.model.QrCodeInfo;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShippingInfoService extends BaseServiceImpl<ShippingInfo, ShippingInfoDto, ShippingInfoDto> {

    private final ShippingInfoRepository shippingInfoRepository;
    private final ShippingLineRepository shippingLineRepository;
    private final ProjetRepository projetRepository;
    private final clientInventaire inventaireClient;

    public ShippingInfoService(
            BaseRepository<ShippingInfo> repository,
            CodeGenerator codeGenerator,
            ModelMapper modelMapper,
            ShippingInfoRepository shippingInfoRepository,
            ShippingLineRepository shippingLineRepository,
            ProjetRepository projetRepository,
            clientInventaire inventaireClient
    ) {
        super(repository, codeGenerator, modelMapper);
        this.shippingInfoRepository = shippingInfoRepository;
        this.shippingLineRepository = shippingLineRepository;
        this.projetRepository = projetRepository;
        this.inventaireClient = inventaireClient;
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingInfoDto findById(UUID id) {
        return getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShippingInfoDto> findAll() {
        return shippingInfoRepository.findAllByIsDeletedFalseOrderByCreatedDateDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShippingInfoDto getById(UUID shippingId) {
        ShippingInfo shipping = shippingInfoRepository.findByIdAndIsDeletedFalse(shippingId)
                .orElseThrow(() -> new EntityNotFoundException("Shipping introuvable : " + shippingId));
        return toDto(shipping);
    }

    @Transactional
    public ShippingInfoDto getOrCreateByProjectId(UUID projectId) {
        ShippingInfo shipping = getOrCreateEntityByProjectId(projectId);
        return toDto(shipping);
    }

    @Transactional
    public ShippingInfoDto upsertProjectShipping(UUID projectId, ShippingInfoUpsertRequest request) {
        ShippingInfo shipping = getOrCreateEntityByProjectId(projectId);
        applyInfo(shipping, request);
        ShippingInfo saved = shippingInfoRepository.save(shipping);
        return toDto(saved);
    }

    @Transactional
    public ShippingInfoDto addLine(UUID projectId, ShippingLineCreateRequest request) {
        ShippingInfo shipping = getOrCreateEntityByProjectId(projectId);
        ensureStatusEditable(shipping.getStatus());

        ArticleSecDto article = inventaireClient.getArticleById(request.getArticleId());
        if (article == null) {
            throw new IllegalArgumentException("Article introuvable : " + request.getArticleId());
        }

        ShippingLine line = new ShippingLine();
        line.setShippingInfo(shipping);
        line.setArticleId(request.getArticleId());
        line.setArticleNameSnapshot(article.getNom());
        line.setQuantity(request.getQuantity());
        line.setUnit(resolveUnit(request.getUnit()));

        shipping.getLines().add(line);
        ShippingInfo saved = shippingInfoRepository.save(shipping);
        return toDto(saved);
    }

    @Transactional
    public ShippingInfoDto removeLine(UUID shippingId, UUID lineId) {
        ShippingInfo shipping = shippingInfoRepository.findByIdAndIsDeletedFalse(shippingId)
                .orElseThrow(() -> new EntityNotFoundException("Shipping introuvable : " + shippingId));
        ensureStatusEditable(shipping.getStatus());

        ShippingLine line = shippingLineRepository.findByIdAndShippingInfoIdAndIsDeletedFalse(lineId, shippingId)
                .orElseThrow(() -> new EntityNotFoundException("Ligne introuvable : " + lineId));

        shipping.getLines().removeIf(existing -> existing.getId().equals(line.getId()));
        shippingLineRepository.delete(line);
        ShippingInfo saved = shippingInfoRepository.save(shipping);
        return toDto(saved);
    }

    @Transactional
    public ShippingInfoDto addEvent(UUID shippingId, ShippingEventCreateRequest request) {
        ShippingInfo shipping = shippingInfoRepository.findByIdAndIsDeletedFalse(shippingId)
                .orElseThrow(() -> new EntityNotFoundException("Shipping introuvable : " + shippingId));

        ShippingEvent event = new ShippingEvent();
        event.setShippingInfo(shipping);
        event.setType(request.getType());
        event.setEventAt(LocalDateTime.now());
        event.setLocation(request.getLocation());
        event.setComment(request.getComment());
        shipping.getEvents().add(event);

        ShippingInfo saved = shippingInfoRepository.save(shipping);
        return toDto(saved);
    }

    @Transactional
    public ShippingInfoDto updateStatus(UUID shippingId, ShippingStatusUpdateRequest request) {
        ShippingInfo shipping = shippingInfoRepository.findByIdAndIsDeletedFalse(shippingId)
                .orElseThrow(() -> new EntityNotFoundException("Shipping introuvable : " + shippingId));

        ShippingStatus current = shipping.getStatus();
        ShippingStatus target = request.getStatus();
        if (!isTransitionAllowed(current, target)) {
            throw new IllegalStateException("Transition de statut invalide : " + current + " -> " + target);
        }

        shipping.setStatus(target);
        updateLifecycleDates(shipping, target);

        ShippingEvent event = new ShippingEvent();
        event.setShippingInfo(shipping);
        event.setType(mapStatusToEvent(target));
        event.setEventAt(LocalDateTime.now());
        event.setLocation(request.getLocation());
        event.setComment(request.getComment());
        shipping.getEvents().add(event);

        ShippingInfo saved = shippingInfoRepository.save(shipping);
        return toDto(saved);
    }

    @Transactional
    public ShippingInfo ensureShippingInfoForProject(Projet projet) {
        if (projet == null || projet.getId() == null) {
            throw new IllegalArgumentException("Projet invalide pour creation du shipping");
        }

        return shippingInfoRepository.findByProjetIdAndIsDeletedFalse(projet.getId())
                .orElseGet(() -> {
                    ShippingInfo created = new ShippingInfo();
                    created.setShippingNumber(generateShippingNumber());
                    created.setProjet(projet);
                    created.setStatus(ShippingStatus.DRAFT);

                    ShippingEvent event = new ShippingEvent();
                    event.setShippingInfo(created);
                    event.setType(ShippingEventType.CREATED);
                    event.setEventAt(LocalDateTime.now());
                    event.setComment("Shipping cree automatiquement a la creation du projet");
                    created.getEvents().add(event);

                    ShippingInfo saved = shippingInfoRepository.save(created);

                    if (saved.getQrHex() == null || saved.getQrHex().isBlank()) {
                        QrCodeInfo qrInfo = generateQrInfo(getEntityType(), saved.getId());
                        saved.setQrHex(qrInfo.getPublicCode());
                        saved.setQrImageBase64(qrInfo.getQrImageBase64());
                        saved = shippingInfoRepository.save(saved);
                    }

                    return saved;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        if (publicCode == null || publicCode.isBlank()) {
            throw new IllegalArgumentException("Le code shipping est obligatoire");
        }

        String normalized = publicCode.trim().toUpperCase(Locale.ROOT);

        Optional<ShippingInfo> match = shippingInfoRepository.findByQrHexAndIsDeletedFalse(normalized);
        if (match.isEmpty()) {
            match = shippingInfoRepository.findByShippingNumberIgnoreCaseAndIsDeletedFalse(normalized);
        }

        ShippingInfo shipping = match.orElseThrow(() ->
                new EntityNotFoundException("Shipping introuvable pour le code : " + publicCode));

        QrResolveResponse response = new QrResolveResponse();
        response.setEntityType(getEntityType());
        response.setPublicCode(normalized);
        response.setEntityId(shipping.getId().toString());
        response.setLabel(shipping.getShippingNumber());
        response.setStatus(shipping.getStatus() != null ? shipping.getStatus().name() : null);
        response.setMobileRoute(getMobileRoute());
        response.setWebRoute(getWebRoute(shipping));
        response.setData(toDto(shipping));
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

    public byte[] generateQrImageFromEntity(ShippingInfo entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("Shipping invalide pour generation QR");
        }

        if (entity.getQrHex() != null && !entity.getQrHex().isBlank()) {
            return generateQrImage(entity.getQrHex());
        }

        QrCodeInfo qrInfo = generateQrInfo(getEntityType(), entity.getId());
        return generateQrImage(qrInfo.getPublicCode());
    }

    @Override
    protected String getEntityType() {
        return "SHIPPING";
    }

    @Override
    protected String getLabel(ShippingInfo entity) {
        return entity.getShippingNumber();
    }

    @Override
    protected String getStatus(ShippingInfo entity) {
        return entity.getStatus() != null ? entity.getStatus().name() : null;
    }

    @Override
    protected String getMobileRoute() {
        return "/projets/detail";
    }

    @Override
    protected String getWebRoute(ShippingInfo entity) {
        if (entity == null || entity.getProjet() == null || entity.getProjet().getId() == null) {
            return "/projets";
        }
        return "/projets/detail/" + entity.getProjet().getId() + "/shipping";
    }

    private ShippingInfo getOrCreateEntityByProjectId(UUID projectId) {
        return shippingInfoRepository.findByProjetIdAndIsDeletedFalse(projectId)
                .orElseGet(() -> {
                    Projet projet = projetRepository.findByIdAndIsDeletedFalse(projectId)
                            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + projectId));
                    return ensureShippingInfoForProject(projet);
                });
    }

    private void applyInfo(ShippingInfo shipping, ShippingInfoUpsertRequest request) {
        if (request == null) {
            return;
        }
        shipping.setDestination(request.getDestination());
        shipping.setIncoterm(normalizeNullable(request.getIncoterm()));
        shipping.setCarrierName(request.getCarrierName());
        shipping.setDriverName(request.getDriverName());
        shipping.setTruckNumber(request.getTruckNumber());
        shipping.setTrackingNumber(request.getTrackingNumber());
        shipping.setExpectedShipDate(request.getExpectedShipDate());
        shipping.setNotes(request.getNotes());
    }

    private String resolveUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "UNIT";
        }
        return unit.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureStatusEditable(ShippingStatus status) {
        if (status == ShippingStatus.DELIVERED || status == ShippingStatus.CANCELLED) {
            throw new IllegalStateException("Modification interdite pour un shipping " + status);
        }
    }

    private boolean isTransitionAllowed(ShippingStatus current, ShippingStatus target) {
        if (current == target) {
            return true;
        }
        return switch (current) {
            case DRAFT -> target == ShippingStatus.READY_TO_SHIP || target == ShippingStatus.CANCELLED;
            case READY_TO_SHIP -> target == ShippingStatus.IN_TRANSIT || target == ShippingStatus.CANCELLED;
            case IN_TRANSIT -> target == ShippingStatus.ARRIVED || target == ShippingStatus.DELIVERED;
            case ARRIVED -> target == ShippingStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private ShippingEventType mapStatusToEvent(ShippingStatus status) {
        return switch (status) {
            case DRAFT -> ShippingEventType.CREATED;
            case READY_TO_SHIP -> ShippingEventType.READY;
            case IN_TRANSIT -> ShippingEventType.DEPARTED;
            case ARRIVED -> ShippingEventType.ARRIVED;
            case DELIVERED -> ShippingEventType.DELIVERED;
            case CANCELLED -> ShippingEventType.CANCELLED;
        };
    }

    private void updateLifecycleDates(ShippingInfo shipping, ShippingStatus status) {
        LocalDateTime now = LocalDateTime.now();
        if (status == ShippingStatus.IN_TRANSIT) {
            shipping.setDepartedAt(now);
        } else if (status == ShippingStatus.ARRIVED) {
            shipping.setArrivedAt(now);
        } else if (status == ShippingStatus.DELIVERED) {
            shipping.setDeliveredAt(now);
            if (shipping.getArrivedAt() == null) {
                shipping.setArrivedAt(now);
            }
        }
    }

    private ShippingInfoDto toDto(ShippingInfo shipping) {
        ShippingInfoDto dto = new ShippingInfoDto();
        dto.setId(shipping.getId());
        dto.setShippingNumber(shipping.getShippingNumber());
        dto.setStatus(shipping.getStatus());
        dto.setDestination(shipping.getDestination());
        dto.setIncoterm(shipping.getIncoterm());
        dto.setCarrierName(shipping.getCarrierName());
        dto.setDriverName(shipping.getDriverName());
        dto.setTruckNumber(shipping.getTruckNumber());
        dto.setTrackingNumber(shipping.getTrackingNumber());
        dto.setExpectedShipDate(shipping.getExpectedShipDate());
        dto.setDepartedAt(shipping.getDepartedAt());
        dto.setArrivedAt(shipping.getArrivedAt());
        dto.setDeliveredAt(shipping.getDeliveredAt());
        dto.setNotes(shipping.getNotes());
        dto.setCreatedDate(shipping.getCreatedDate());
        dto.setLastModifiedDate(shipping.getLastModifiedDate());
        dto.setPublicCode(shipping.getQrHex());
        dto.setQrImageBase64(shipping.getQrImageBase64());

        if (shipping.getProjet() != null) {
            dto.setProjectId(shipping.getProjet().getId());
            dto.setProjectCode(shipping.getProjet().getCode());
        }

        List<ShippingLineDto> lines = shipping.getLines().stream()
                .sorted(Comparator.comparing(ShippingLine::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toLineDto)
                .collect(Collectors.toList());
        dto.setLines(lines);

        List<ShippingEventDto> events = shipping.getEvents().stream()
                .sorted(Comparator.comparing(ShippingEvent::getEventAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toEventDto)
                .collect(Collectors.toList());
        dto.setEvents(events);

        return dto;
    }

    private ShippingLineDto toLineDto(ShippingLine line) {
        ShippingLineDto dto = new ShippingLineDto();
        dto.setId(line.getId());
        dto.setArticleId(line.getArticleId());
        dto.setArticleName(line.getArticleNameSnapshot());
        dto.setQuantity(line.getQuantity());
        dto.setUnit(line.getUnit());
        return dto;
    }

    private ShippingEventDto toEventDto(ShippingEvent event) {
        ShippingEventDto dto = new ShippingEventDto();
        dto.setId(event.getId());
        dto.setType(event.getType());
        dto.setEventAt(event.getEventAt());
        dto.setLocation(event.getLocation());
        dto.setComment(event.getComment());
        return dto;
    }

    private String generateShippingNumber() {
        return "SHP-" + System.currentTimeMillis();
    }
}
