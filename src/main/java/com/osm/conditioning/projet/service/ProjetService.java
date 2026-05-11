package com.osm.conditioning.projet.service;

import com.osm.conditioning.projet.dto.ClientDto;
import com.osm.conditioning.projet.dto.ProjetDto;
import com.osm.conditioning.projet.entity.Client;
import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.repository.ClientRepository;
import com.osm.conditioning.projet.repository.ProjetRepository;
import com.osm.conditioning.shipping.service.ShippingInfoService;

import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.model.QrCodeInfo;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjetService extends BaseServiceImpl<Projet, ProjetDto, ProjetDto> {

    private static final String STATUT_BROUILLON = "BROUILLON";
    private static final String STATUT_ANNULE = "ANNULE";
    private static final String CODE_PREFIX = "PRJ-";
    private static final String ENTITY_TYPE = "PROJET";

    private final ProjetRepository projetRepository;
     private final ShippingInfoService shippingInfoService;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    public ProjetService(
            BaseRepository<Projet> repository,
            CodeGenerator codeGenerator,
            ModelMapper modelMapper,
            ProjetRepository projetRepository,
            ShippingInfoService shippingInfoService,
            ClientService clientService, ClientRepository clientRepository
    ) {
        super(repository, codeGenerator, modelMapper);
        this.projetRepository = projetRepository;
        this.clientService = clientService;
         this.shippingInfoService = shippingInfoService;
        this.clientRepository = clientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjetDto> findAll() {
        return projetRepository.findAllByTenantIdAndIsDeletedFalse(TenantContext.getCurrentTenant())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        if (publicCode == null || publicCode.isBlank()) {
            throw new IllegalArgumentException("Le code est obligatoire");
        }

        String normalizedCode = publicCode.trim().toUpperCase(java.util.Locale.ROOT);
        UUID tenantId = TenantContext.getCurrentTenant();

        // 1. Recherche par qrHex
        Optional<Projet> entity = (tenantId == null)
                ? projetRepository.findByQrHex(normalizedCode)
                : projetRepository.findByQrHexAndTenantIdAndIsDeletedFalse(normalizedCode, tenantId);

        // 2. Fallback par qrHex (tenant-agnostic) si non trouve
        if (entity.isEmpty() && tenantId != null) {
            entity = projetRepository.findByQrHex(normalizedCode);
        }

        // 3. Fallback par code métier (PRJ-...)
        if (entity.isEmpty()) {
            entity = (tenantId == null)
                    ? projetRepository.findByCodeIgnoreCaseAndIsDeletedFalse(normalizedCode)
                    : projetRepository.findByCodeIgnoreCaseAndTenantIdAndIsDeletedFalse(normalizedCode, tenantId);
        }

        // 4. Fallback par code métier (tenant-agnostic)
        if (entity.isEmpty() && tenantId != null) {
            entity = projetRepository.findByCodeIgnoreCaseAndIsDeletedFalse(normalizedCode);
        }

        return entity.map(p -> buildResolveResponse(normalizedCode, p))
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve pour le code : " + publicCode));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrResolveResponse> searchByCode(String code) {
        try {
            return Optional.ofNullable(resolve(code));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjetDto findById(UUID id) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));
        return toDto(projet);
    }

    @Override
    protected String getEntityType() {
        return ENTITY_TYPE;
    }

    @Override
    protected String getLabel(Projet entity) {
        return entity.getCode();
    }

    @Override
    protected String getStatus(Projet entity) {
        return entity.getStatut();
    }

    @Override
    protected String getMobileRoute() {
        return "/projets/detail";
    }

    @Override
    protected String getWebRoute(Projet entity) {
        return "/projets/detail/" + entity.getId();
    }

    /**
     * Correction:
     * Ne pas override searchByCode(String) ici.
     * Cette methode existe deja dans BaseServiceImpl avec un type de retour different.
     * On utilise donc une methode applicative distincte pour la recherche par code projet/qr.
     */
    @Transactional(readOnly = true)
    public ProjetDto findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code projet est obligatoire");
        }

        UUID tenantId = TenantContext.getCurrentTenant();
        String normalizedCode = code.trim().toUpperCase();

        Projet projet = (tenantId == null)
                ? projetRepository.findByCodeAndIsDeletedFalse(normalizedCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Projet introuvable pour le code : " + normalizedCode))
                : projetRepository.findByCodeAndTenantIdAndIsDeletedFalse(normalizedCode, tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Projet introuvable pour le code : " + normalizedCode));

        return toDto(projet);
    }

    /**
     * Recherche unique via qrHex puis fallback sur le code manuel.
     */
    @Transactional(readOnly = true)
    public ProjetDto findByUniqueCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code est obligatoire");
        }

        String normalized = code.trim().toUpperCase();
        UUID tenantId = TenantContext.getCurrentTenant();

        Optional<Projet> projet = (tenantId == null)
                ? projetRepository.findByQrHex(normalized)
                : projetRepository.findByQrHexAndTenantIdAndIsDeletedFalse(normalized, tenantId);

        if (projet.isEmpty()) {
            projet = (tenantId == null)
                    ? projetRepository.findByCodeAndIsDeletedFalse(normalized)
                    : projetRepository.findByCodeAndTenantIdAndIsDeletedFalse(normalized, tenantId);
        }

        return projet.map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable pour le code : " + normalized));
    }

    @Transactional
    public ProjetDto create(ProjetDto dto) {
        if (dto.getClient() == null || dto.getClient().getId() == null) {
            throw new IllegalArgumentException("Client is required");
        }

        Client client = clientRepository.findById(dto.getClient().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Client not found with id: " + dto.getClient().getId()
                ));

        Projet projet = new Projet();
        projet.setCode(generateCode());
        projet.setClient(client);

        applyBusinessFields(projet, dto);

        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            projet.setTenantId(tenantId);
        }

        Projet saved = projetRepository.saveAndFlush(projet);

        if (saved.getQrHex() == null || saved.getQrHex().isBlank()) {
            QrCodeInfo qrInfo = generateQrInfo(getEntityType(), saved.getId());

            saved.setQrHex(qrInfo.getPublicCode());
            saved.setQrImageBase64(qrInfo.getQrImageBase64());

            saved = projetRepository.save(saved);
        }

        shippingInfoService.ensureShippingInfoForProject(saved);

        return toDto(saved);
    }
    @Override
    @Transactional
    public ProjetDto save(ProjetDto dto) {
        return create(dto);
    }

    @Transactional
    public ProjetDto update(UUID id, ProjetDto dto) {

        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Projet non trouve : " + id));
        Client client = clientService.findClientEntityById(dto.getId());

        projet.setClient(client);

        applyBusinessFields(projet, dto);

        Projet saved = projetRepository.save(projet);

        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjetDto update(ProjetDto dto) {

        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException(
                    "L'id du projet est obligatoire pour la mise a jour");
        }

        return update(dto.getId(), dto);
    }

    @Transactional
    public ProjetDto cancel(UUID id) {

        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Projet non trouve : " + id));

        projet.setStatut(STATUT_ANNULE);

        Projet saved = projetRepository.save(projet);

        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjetDto delete(UUID id) {

        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Projet non trouve : " + id));

        projet.setDeleted(true);

        Projet saved = projetRepository.save(projet);

        return toDto(saved);
    }

    @Transactional
    public ProjetDto updateStatus(UUID id, String statut) {

        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Projet non trouve : " + id));

        if (statut == null || statut.isBlank()) {
            throw new IllegalArgumentException("Le statut est obligatoire");
        }

        projet.setStatut(statut.trim().toUpperCase());

        Projet saved = projetRepository.save(projet);

        return toDto(saved);
    }

    @Transactional
    public ProjetDto updateStatusByCode(String code, String statut) {

        ProjetDto projetDto = findByUniqueCode(code);

        return updateStatus(projetDto.getId(), statut);
    }

    @Transactional(readOnly = true)
    public Projet findByIdOrThrow(UUID id) {

        return projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Projet non trouve : " + id));
    }

    public byte[] generateQrImageFromEntity(Projet entity) {

        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException(
                    "Projet invalide pour generation QR");
        }

        if (entity.getQrHex() != null && !entity.getQrHex().isBlank()) {
            return generateQrImage(entity.getQrHex());
        }

        QrCodeInfo qrInfo = generateQrInfo(ENTITY_TYPE, entity.getId());

        return generateQrImage(qrInfo.getPublicCode());
    }

    private void applyBusinessFields(Projet projet, ProjetDto dto) {

        projet.setTypeProduit(dto.getTypeProduit());
        projet.setTypeEmballage(dto.getTypeEmballage());
        projet.setQuantiteCible(dto.getQuantiteCible());
        projet.setUnite(dto.getUnite());
        projet.setDateLimiteLivraison(dto.getDateLimiteLivraison());
        projet.setPrixUnitaire(dto.getPrixUnitaire());
        projet.setConditionsLivraison(dto.getConditionsLivraison());
        projet.setSkuId(dto.getSkuId());
        projet.setBomId(dto.getBomId());

        if (dto.getStatut() != null && !dto.getStatut().isBlank()) {
            projet.setStatut(dto.getStatut());
        } else if (projet.getStatut() == null || projet.getStatut().isBlank()) {
            projet.setStatut(STATUT_BROUILLON);
        }

        projet.setValeurTotale(
                calculateValeurTotale(
                        dto.getQuantiteCible(),
                        dto.getPrixUnitaire()
                )
        );
    }

    private BigDecimal calculateValeurTotale(
            Double quantiteCible,
            BigDecimal prixUnitaire
    ) {

        if (quantiteCible == null || prixUnitaire == null) {
            return BigDecimal.ZERO;
        }

        return prixUnitaire.multiply(BigDecimal.valueOf(quantiteCible));
    }

    private String generateCode() {
        return CODE_PREFIX + System.currentTimeMillis();
    }

    private ProjetDto toDto(Projet projet) {

        ProjetDto dto = new ProjetDto();

        dto.setId(projet.getId());
        dto.setCode(projet.getCode());

        if (projet.getClient() != null) {
            dto.setClient(modelMapper.map(projet.getClient(), ClientDto.class));
        }

        dto.setTypeProduit(projet.getTypeProduit());
        dto.setTypeEmballage(projet.getTypeEmballage());
        dto.setQuantiteCible(projet.getQuantiteCible());
        dto.setUnite(projet.getUnite());
        dto.setDateLimiteLivraison(projet.getDateLimiteLivraison());
        dto.setPrixUnitaire(projet.getPrixUnitaire());
        dto.setValeurTotale(projet.getValeurTotale());
        dto.setConditionsLivraison(projet.getConditionsLivraison());
        dto.setStatut(projet.getStatut());
        dto.setCreatedDate(projet.getCreatedDate());
        dto.setPublicCode(projet.getQrHex());
        dto.setQrImageBase64(projet.getQrImageBase64());
        dto.setSkuId(projet.getSkuId());
        dto.setBomId(projet.getBomId());

        if (projet.getOrdresFabrication() != null
                && !projet.getOrdresFabrication().isEmpty()) {

            dto.setNombreOF(projet.getOrdresFabrication().size());

            double totalProduit = projet.getOrdresFabrication()
                    .stream()
                    .filter(of -> of.getQuantiteBonne() != null)
                    .mapToDouble(of -> of.getQuantiteBonne().doubleValue())
                    .sum();

            dto.setQuantiteProduite(totalProduit);

            if (projet.getQuantiteCible() != null
                    && projet.getQuantiteCible() > 0) {

                double taux =
                        (totalProduit / projet.getQuantiteCible()) * 100.0;

                dto.setTauxAvancement(
                        Math.min(
                                100.0,
                                Math.round(taux * 100.0) / 100.0
                        )
                );

            } else {
                dto.setTauxAvancement(0.0);
            }

        } else {

            dto.setNombreOF(0);
            dto.setQuantiteProduite(0.0);
            dto.setTauxAvancement(0.0);
        }

        return dto;
    }
}