package com.osm.production.projet.service;

import com.osm.production.projet.dto.ProjetDto;
import com.osm.production.projet.entity.Projet;
import com.osm.production.projet.entity.ProjetClient;
import com.osm.production.projet.repository.ProjetRepository;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.model.QrCodeInfo;
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

    private final ProjetRepository projetRepository;
    private final ProjetClientService projetClientService;

    public ProjetService(
            BaseRepository<Projet> repository,
            CodeGenerator codeGenerator,
            ModelMapper modelMapper,
            ProjetRepository projetRepository,
            ProjetClientService projetClientService) {
        super(repository, codeGenerator, modelMapper);
        this.projetRepository = projetRepository;
        this.projetClientService = projetClientService;
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
    public ProjetDto findById(UUID id) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));
        return toDto(projet);
    }

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
        ProjetClient client = projetClientService.findByIdOrThrow(dto.getClientId());

        Projet projet = new Projet();
        projet.setCode(generateCode());
        projet.setClient(client);
        applyBusinessFields(projet, dto);

        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            projet.setTenantId(tenantId);
        }

        Projet saved = projetRepository.save(projet);

        if (saved.getQrHex() == null || saved.getQrHex().isBlank()) {
            QrCodeInfo qrInfo = generateQrInfo(saved.getId());
            saved.setQrHex(qrInfo.getPublicCode());
            saved.setQrImageBase64(qrInfo.getQrImageBase64());
            saved = projetRepository.save(saved);
        }

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
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));

        ProjetClient client = projetClientService.findByIdOrThrow(dto.getClientId());

        projet.setClient(client);
        applyBusinessFields(projet, dto);

        Projet saved = projetRepository.save(projet);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjetDto update(ProjetDto dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("L'id du projet est obligatoire pour la mise a jour");
        }
        return update(dto.getId(), dto);
    }

    @Transactional
    public ProjetDto cancel(UUID id) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));

        projet.setStatut(STATUT_ANNULE);

        Projet saved = projetRepository.save(projet);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjetDto delete(UUID id) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));

        projet.setDeleted(true);
        Projet saved = projetRepository.save(projet);
        return toDto(saved);
    }

    @Transactional
    public ProjetDto updateStatus(UUID id, String statut) {
        Projet projet = projetRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));

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
                .orElseThrow(() -> new EntityNotFoundException("Projet non trouve : " + id));
    }

    public byte[] generateQrImageFromEntity(Projet entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("Projet invalide pour generation QR");
        }

        if (entity.getQrHex() != null && !entity.getQrHex().isBlank()) {
            return generateQrImage(entity.getQrHex());
        }

        QrCodeInfo qrInfo = generateQrInfo(entity.getId());
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

        if (dto.getStatut() != null && !dto.getStatut().isBlank()) {
            projet.setStatut(dto.getStatut());
        } else if (projet.getStatut() == null || projet.getStatut().isBlank()) {
            projet.setStatut(STATUT_BROUILLON);
        }

        projet.setValeurTotale(calculateValeurTotale(dto.getQuantiteCible(), dto.getPrixUnitaire()));
    }

    private BigDecimal calculateValeurTotale(Double quantiteCible, BigDecimal prixUnitaire) {
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
            dto.setClientId(projet.getClient().getId());
            dto.setClientNom(projet.getClient().getNom());
            dto.setClientEmail(projet.getClient().getEmail());
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

        dto.setQrCode(projet.getQrHex());
        dto.setQrImageBase64(projet.getQrImageBase64());

        return dto;
    }
}
