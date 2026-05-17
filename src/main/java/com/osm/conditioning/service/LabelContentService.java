package com.osm.conditioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionDelivery;
import com.osm.conditioning.client.clientProductionFiltration;
import com.osm.conditioning.client.clientProductionOilTransaction;
import com.osm.conditioning.client.clientProductionStorage;
import com.osm.conditioning.client.clientSecurityCompanyProfile;
import com.osm.conditioning.dto.ProduitFinalDto;
import com.osm.conditioning.model.LabelContent;
import com.osm.conditioning.model.LabelSource;
import com.osm.conditioning.repository.LabelContentRepository;
import com.osm.conditioning.repository.CertificationRepository;
import com.xdev.communicator.models.enums.*;
import com.xdev.communicator.models.shared.*;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.utils.OSMLogger;
import com.xdev.xdevbase.utils.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LabelContentService {

    private static final int DEFAULT_SHELF_LIFE_MONTHS = 12;
    private static final String DEFAULT_ORIGIN_COUNTRY = "Tunisie";
    private static final String DEFAULT_STORAGE_CONDITIONS = "A conserver a l'abri de la lumiere et de la chaleur";
    private static final DateTimeFormatter BEST_BEFORE_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private static final Map<QualityGrades, String> OFFICIAL_NAMES = Map.of(
            QualityGrades.EXTRA_VIRGIN, "Huile d'olive vierge extra",
            QualityGrades.VIRGIN, "Huile d'olive vierge",
            QualityGrades.REFINED, "Huile d'olive raffinee",
            QualityGrades.LAMPANTE, "Huile d'olive lampante",
            QualityGrades.OTHER, "Huile d'olive");

    private static final Map<LabelClaimType, String> MARKETING_CLAIMS = Map.of(
            LabelClaimType.MADE_IN_TUNISIA, "Made in Tunisia",
            LabelClaimType.BIO, "BIO",
            LabelClaimType.COLD_EXTRACTION, "Extraction a froid",
            LabelClaimType.PRIVATE_LABEL, "Private Label",
            LabelClaimType.OTHER, "Autre claim");

    private final LabelContentRepository labelContentRepository;
    private final clientInventaire clientInventaire;
    private final clientProductionStorage clientProductionStorage;
    private final clientProductionDelivery clientProductionDelivery;
    private final clientSecurityCompanyProfile clientSecurityCompanyProfile;
    private final ObjectMapper objectMapper;
    private final CodeGenerator codeGenerator;
    private final CertificationRepository certificationRepository;
    private final clientProductionFiltration clientProductionFiltration;

    public LabelContentService(
            LabelContentRepository labelContentRepository,
            clientInventaire clientInventaire,
            clientProductionStorage clientProductionStorage,
            clientProductionDelivery clientProductionDelivery,
            clientSecurityCompanyProfile clientSecurityCompanyProfile,
            ObjectMapper objectMapper,
            CodeGenerator codeGenerator,
            CertificationRepository certificationRepository,
            clientProductionFiltration clientProductionFiltration) {
        this.labelContentRepository = labelContentRepository;
        this.clientInventaire = clientInventaire;
        this.clientProductionStorage = clientProductionStorage;
        this.clientProductionDelivery = clientProductionDelivery;
        this.clientSecurityCompanyProfile = clientSecurityCompanyProfile;
        this.objectMapper = objectMapper;
        this.codeGenerator = codeGenerator;
        this.certificationRepository = certificationRepository;
        this.clientProductionFiltration = clientProductionFiltration;
    }

    @Transactional(readOnly = true)
    public List<LabelContentDto> getAll() {
        return labelContentRepository.findAll()
                .stream()
                .filter(labelContent -> !Boolean.TRUE.equals(labelContent.getDeleted()))
                .map(labelContent -> toDto(labelContent, validateLabel(labelContent)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabelContentDto> getByProductId(UUID productId) {
        Map<UUID, LabelContent> unique = new LinkedHashMap<>();

        for (LabelContent labelContent : labelContentRepository.findAllByProductIdAndIsDeletedFalse(productId)) {
            unique.put(labelContent.getId(), labelContent);
        }

        for (LabelContent labelContent : labelContentRepository.findAllByPackagingIdAndIsDeletedFalse(productId)) {
            unique.putIfAbsent(labelContent.getId(), labelContent);
        }

        return unique.values().stream()
                .map(labelContent -> toDto(labelContent, validateLabel(labelContent)))
                .toList();
    }

    @Transactional
    public LabelContentDto generate(LabelGenerateRequestDto request) {
        UserContext currentUser = fetchCurrentUser();

        ApiResponse<StorageUnitDto> response = clientProductionStorage.getStorageUnit(request.getLotId());
        if (response == null || response.getData() == null) {
            throw new EntityNotFoundException("Lot filtre introuvable");
        }

        StorageUnitDto storageUnit = response.getData();
        ProduitFinalDto packaging = clientInventaire.getProduitFinalById(request.getPackagingId());
        UUID productId = request.getProductId() != null ? request.getProductId() : packaging.getId();
        LabelContent latestProductLabel = findLatestLabelByProduct(productId);

        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant courant introuvable");
        }

        CompanyProfileDto companyProfile = clientSecurityCompanyProfile.getByTenantId(tenantId);

        LabelContent labelContent = new LabelContent();
        labelContent.setLotId(request.getLotId());
        labelContent.setProductId(productId);
        labelContent.setPackagingId(request.getPackagingId());
        labelContent.setOperatorId(currentUser.id());
        labelContent.setLanguage(Optional.ofNullable(request.getLanguage()).orElse(LabelLanguage.FR));
        labelContent.setPackagingDate(Optional.ofNullable(request.getPackagingDate()).orElse(LocalDate.now()));
        labelContent.setLabelCategory(Optional.ofNullable(request.getLabelCategory()).orElse(LabelCategory.UNIT));
        labelContent.setFiltrationOperationId(request.getFiltrationOperationId());
        labelContent.setStatus(LabelContentStatus.DRAFT);

        prepareLabelContent(labelContent, storageUnit, packaging, companyProfile);
        applyReusableDefaultsFromExistingLabel(labelContent, latestProductLabel);

        applyRequestQualityAndVariety(
                labelContent,
                request.getQualityGrade(),
                request.getVariety()
        );

        saveSourceProofs(labelContent, storageUnit, packaging, currentUser, companyProfile, request.getFiltrationOperationId());

        LabelContent saved = ensureQrCode(labelContentRepository.save(labelContent));
        return toDto(saved, validateLabel(saved));
    }

    @Transactional(readOnly = true)
    public LabelContentDto getById(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);
        return toDto(labelContent, validateLabel(labelContent));
    }

    @Transactional
    public LabelContentDto update(UUID id, LabelContentUpdateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("La requete de mise a jour est obligatoire");
        }

        LabelContent labelContent = findLabelOrThrow(id);

        if (labelContent.getStatus() == LabelContentStatus.FINALIZED) {
            throw new IllegalStateException("Le contenu finalise ne peut plus etre modifie");
        }

        boolean updated = applyUpdate(labelContent, request);

        if (updated && labelContent.getStatus() == LabelContentStatus.VALIDATED) {
            labelContent.setStatus(LabelContentStatus.DRAFT);
        }

        labelContent.setFinalPayloadJson(null);
        labelContent.setFinalizedAt(null);
        labelContent.setFinalizedBy(null);

        LabelContent saved = labelContentRepository.save(labelContent);
        return toDto(saved, validateLabel(saved));
    }

    @Transactional
    public void delete(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);

        if (labelContent.getStatus() == LabelContentStatus.FINALIZED) {
            throw new IllegalStateException(
                    "Une etiquette finalisee ne peut pas etre supprimee pour garantir la tracabilite");
        }

        labelContent.setDeleted(true);
        labelContentRepository.save(labelContent);
    }

    @Transactional
    public LabelContentDto markAsDraft(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);

        if (labelContent.getStatus() == LabelContentStatus.FINALIZED) {
            throw new IllegalStateException("Une etiquette finalisee ne peut pas etre remise en brouillon");
        }

        labelContent.setStatus(LabelContentStatus.DRAFT);
        labelContent.setFinalPayloadJson(null);
        labelContent.setFinalizedAt(null);
        labelContent.setFinalizedBy(null);

        LabelContent saved = labelContentRepository.save(labelContent);
        return toDto(saved, validateLabel(saved));
    }

    @Transactional(readOnly = true)
    public LabelExportDto export(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);

        if (labelContent.getStatus() != LabelContentStatus.FINALIZED) {
            throw new IllegalStateException("Seule une etiquette finalisee peut etre exportee");
        }

        return toExportDto(labelContent);
    }

    @Transactional
    public LabelContentDto approve(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);

        defaultLegalDenomination(labelContent);

        List<LabelValidationIssueDto> issues = validateLabel(labelContent);

        if (!issues.isEmpty()) {
            throw new IllegalStateException("Le contenu de l'etiquette contient des incoherences bloquantes");
        }

        if (labelContent.getStatus() == LabelContentStatus.FINALIZED && !isBlank(labelContent.getFinalPayloadJson())) {
            return toDto(labelContent, List.of());
        }

        labelContent.setStatus(LabelContentStatus.FINALIZED);
        labelContent.setFinalizedAt(LocalDateTime.now());
        labelContent.setFinalizedBy(fetchCurrentUser().login());
        labelContent.setFinalPayloadJson(exportJson(labelContent));

        LabelContent saved = labelContentRepository.save(labelContent);
        return toDto(saved, List.of());
    }

    private LabelContent findLabelOrThrow(UUID id) {
        return labelContentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Label content introuvable pour l'id: " + id));
    }

    private LabelContent findLatestLabelByProduct(UUID productId) {
        if (productId == null) {
            return null;
        }

        return getByProductEntities(productId).stream()
                .filter(labelContent -> !Boolean.TRUE.equals(labelContent.getDeleted()))
                .max(Comparator.comparing(LabelContent::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<LabelContent> getByProductEntities(UUID productId) {
        Map<UUID, LabelContent> unique = new LinkedHashMap<>();

        for (LabelContent labelContent : labelContentRepository.findAllByProductIdAndIsDeletedFalse(productId)) {
            unique.put(labelContent.getId(), labelContent);
        }

        for (LabelContent labelContent : labelContentRepository.findAllByPackagingIdAndIsDeletedFalse(productId)) {
            unique.putIfAbsent(labelContent.getId(), labelContent);
        }

        return new ArrayList<>(unique.values());
    }

    private LabelContent ensureQrCode(LabelContent labelContent) {
        if (!isBlank(labelContent.getQrHex())) {
            return labelContent;
        }

        labelContent.setQrHex(codeGenerator.generateUnique(labelContentRepository::existsByQrHex));
        return labelContentRepository.save(labelContent);
    }

    private ProduitFinalDto loadPackaging(UUID packagingId) {
        try {
            return clientInventaire.getProduitFinalById(packagingId);
        } catch (Exception ignored) {
            throw new EntityNotFoundException("Packaging introuvable pour l'id: " + packagingId);
        }
    }

    private void prepareLabelContent(
            LabelContent labelContent,
            StorageUnitDto storageUnit,
            ProduitFinalDto packaging,
            CompanyProfileDto companyProfile) {
        labelContent.setLotNumber(storageUnit.getLotNumber());

        labelContent.setLegalDenomination(
                Optional.ofNullable(officialName(storageUnit.getQualityGrade()))
                        .orElse(OFFICIAL_NAMES.get(QualityGrades.OTHER))
        );
        labelContent.setOriginCountry(DEFAULT_ORIGIN_COUNTRY);
        labelContent.setNetQuantity(calculateQuantity(packaging, labelContent.getLabelCategory()));
        labelContent.setBestBeforeDate(expiryDate(labelContent.getPackagingDate()));
        labelContent.setStorageConditions(DEFAULT_STORAGE_CONDITIONS);
        labelContent.setResponsibleName(companyProfile != null ? clean(companyProfile.getLegalName()) : null);

        labelContent.setResponsibleAddress(companyProfile == null ? null
                : joinNonBlank(
                companyProfile.getAddressLine1(),
                companyProfile.getPostalCode(),
                companyProfile.getCity(),
                companyProfile.getGovernorate(),
                DEFAULT_ORIGIN_COUNTRY));

        labelContent.setExtractionMethod(Olive_Oil_Type.OB.getName());
        labelContent.setSensoryProfile("Profil issu du controle qualite");
        labelContent.setClaimTypes(new LinkedHashSet<>());
        labelContent.setMarketingClaims(new ArrayList<>());
        labelContent.setCertifications(new ArrayList<>());
    }

    private void applyReusableDefaultsFromExistingLabel(LabelContent target, LabelContent source) {
        if (source == null) {
            return;
        }

        if (source.getLanguage() != null) {
            target.setLanguage(source.getLanguage());
        }
        if (source.getLabelCategory() != null) {
            target.setLabelCategory(source.getLabelCategory());
        }
        if (!isBlank(source.getLegalDenomination())) {
            target.setLegalDenomination(source.getLegalDenomination());
        }
        if (!isBlank(source.getOriginCountry())) {
            target.setOriginCountry(source.getOriginCountry());
        }
        if (!isBlank(source.getNetQuantity())) {
            target.setNetQuantity(source.getNetQuantity());
        }
        if (!isBlank(source.getStorageConditions())) {
            target.setStorageConditions(source.getStorageConditions());
        }
        if (!isBlank(source.getResponsibleName())) {
            target.setResponsibleName(source.getResponsibleName());
        }
        if (!isBlank(source.getResponsibleAddress())) {
            target.setResponsibleAddress(source.getResponsibleAddress());
        }
        if (!isBlank(source.getQualityGrade())) {
            target.setQualityGrade(source.getQualityGrade());
        }
        if (!isBlank(source.getVariety())) {
            target.setVariety(source.getVariety());
        }
        if (!isBlank(source.getExtractionMethod())) {
            target.setExtractionMethod(source.getExtractionMethod());
        }
        if (!isBlank(source.getSensoryProfile())) {
            target.setSensoryProfile(source.getSensoryProfile());
        }

        target.setCertifications(new ArrayList<>(safeList(source.getCertifications())));
        target.setClaimTypes(new LinkedHashSet<>(safeSet(source.getClaimTypes())));
        target.setMarketingClaims(new ArrayList<>(safeList(source.getMarketingClaims())));
    }

    private void saveSourceProofs(
            LabelContent labelContent,
            StorageUnitDto storageUnit,
            ProduitFinalDto packaging,
            UserContext currentUser,
            CompanyProfileDto companyProfile,
            UUID filtrationOperationId
    ) {
        labelContent.getSourceSnapshots().clear();
        addSnapshot(labelContent, LabelSourceType.FILTERED_LOT, storageUnit.getId(), storageUnit.getLotNumber(), storageUnit);
        addSnapshot(labelContent, LabelSourceType.PACKAGING, packaging.getId(), packaging.getCode(), packaging);
        addSnapshot(labelContent, LabelSourceType.OPERATOR, currentUser.id(), currentUser.displayName(), currentUser.snapshot());
        if (companyProfile != null) {
            addSnapshot(labelContent, LabelSourceType.COMPANY_PROFILE, companyProfile.getId(), companyProfile.getLegalName(), companyProfile);
        }

        if (filtrationOperationId != null) {
            try {
                ApiResponse<Object> opResponse = clientProductionFiltration.getFiltration(filtrationOperationId);
                if (opResponse != null && opResponse.getData() != null) {
                    addSnapshot(labelContent, LabelSourceType.FILTRATION_OPERATION, filtrationOperationId, "OP-" + filtrationOperationId.toString().substring(0, 8), opResponse.getData());
                }
            } catch (Exception e) {
                // Non-blocking for now
                OSMLogger.logException(this.getClass(), "saveSourceProofs - filtration", e);
            }
        }
    }

    private void addSnapshot(
            LabelContent labelContent,
            LabelSourceType type,
            UUID sourceId,
            String businessKey,
            Object payload) {
        LabelSource snapshot = new LabelSource();
        snapshot.setLabelContent(labelContent);
        snapshot.setSourceType(type);
        snapshot.setSourceId(sourceId);
        snapshot.setSourceBusinessKey(businessKey);
        // Sérialisation directe du payload en JSON
        try {
            String json = objectMapper.writeValueAsString(payload);
            snapshot.setSnapshotJson(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de serialiser le snapshot de l'etiquette", e);
        }
        labelContent.getSourceSnapshots().add(snapshot);
    }

    private boolean applyRequestQualityAndVariety(
            LabelContent labelContent,
            String qualityGrade,
            String variety
    ) {
        boolean updated = false;

        if (qualityGrade != null) {
            String value = clean(qualityGrade);

            if (!Objects.equals(value, labelContent.getQualityGrade())) {
                labelContent.setQualityGrade(value);
                updated = true;
            }

            String officialName = officialNameFromString(value);
            if (!isBlank(officialName) && !Objects.equals(officialName, labelContent.getLegalDenomination())) {
                labelContent.setLegalDenomination(officialName);
                updated = true;
            }
        }

        if (variety != null) {
            String value = clean(variety);

            if (!Objects.equals(value, labelContent.getVariety())) {
                labelContent.setVariety(value);
                updated = true;
            }
        }

        return updated;
    }

    private String officialNameFromString(String qualityGrade) {
        if (isBlank(qualityGrade)) {
            return null;
        }

        String normalized = Normalizer.normalize(qualityGrade, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace("'", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");

        if (normalized.contains("EXTRAVIRGIN")
                || (normalized.contains("EXTRA")
                && (normalized.contains("VIRGIN") || normalized.contains("VIERGE")))) {
            return OFFICIAL_NAMES.get(QualityGrades.EXTRA_VIRGIN);
        }

        if (normalized.equals("VIRGIN") || normalized.contains("VIERGE")) {
            return OFFICIAL_NAMES.get(QualityGrades.VIRGIN);
        }

        if (normalized.contains("REFINED") || normalized.contains("RAFFINE")) {
            return OFFICIAL_NAMES.get(QualityGrades.REFINED);
        }

        if (normalized.contains("LAMPANTE") || normalized.contains("LAMPANT")) {
            return OFFICIAL_NAMES.get(QualityGrades.LAMPANTE);
        }

        if (normalized.contains("OLIVEOIL")
                || normalized.equals("OLIVE")
                || normalized.equals("OTHER")
                || normalized.equals("POMACEOIL")) {
            return OFFICIAL_NAMES.get(QualityGrades.OTHER);
        }

        return null;
    }

    private String officialName(QualityGrades qualityGrade) {
        if (qualityGrade != null && OFFICIAL_NAMES.containsKey(qualityGrade)) {
            return OFFICIAL_NAMES.get(qualityGrade);
        }

        String normalized = qualityGrade == null
                ? "EXTRAVIRGIN"
                : Normalizer.normalize(qualityGrade.toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace("'", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");

        if (normalized.contains("EXTRAVIRGIN")
                || (normalized.contains("EXTRA") && (normalized.contains("VIRGIN") || normalized.contains("VIERGE")))) {
            return OFFICIAL_NAMES.get(QualityGrades.EXTRA_VIRGIN);
        }

        if (normalized.contains("VIRGIN") || normalized.contains("VIERGE")) {
            return OFFICIAL_NAMES.get(QualityGrades.VIRGIN);
        }

        if (normalized.contains("REFINED") || normalized.contains("RAFFINE")) {
            return OFFICIAL_NAMES.get(QualityGrades.REFINED);
        }

        if (normalized.contains("LAMPANTE") || normalized.contains("LAMPANT")) {
            return OFFICIAL_NAMES.get(QualityGrades.LAMPANTE);
        }

        return OFFICIAL_NAMES.get(QualityGrades.OTHER);
    }

    private String formatVolume(Float volume) {
        if (volume == null || volume <= 0) {
            return null;
        }

        if (volume >= 1000f) {
            return volume % 1000f == 0
                    ? String.format(Locale.ROOT, "%.0f L", volume / 1000f)
                    : String.format(Locale.ROOT, "%.2f L", volume / 1000f);
        }

        return String.format(Locale.ROOT, "%.0f ml", volume);
    }

    private String calculateQuantity(ProduitFinalDto packaging, LabelCategory category) {
        if (packaging == null) {
            return null;
        }

        float unitVolume = Optional.ofNullable(packaging.getVolume()).orElse(0f);

        if (category == LabelCategory.COLIS) {
            int unitsPerCase = Optional.ofNullable(packaging.getUnitesParCols()).orElse(1);
            return formatVolume(unitVolume * unitsPerCase) + " (" + unitsPerCase + " unites)";
        }

        if (category == LabelCategory.PALLET) {
            int unitsPerCase = Optional.ofNullable(packaging.getUnitesParCols()).orElse(1);
            int casesPerPallet = Optional.ofNullable(packaging.getColisParPalette()).orElse(1);
            int totalUnits = unitsPerCase * casesPerPallet;
            return formatVolume(unitVolume * totalUnits) + " (" + casesPerPallet + " colis)";
        }

        return formatVolume(unitVolume);
    }

    private String expiryDate(LocalDate packagingDate) {
        return packagingDate == null
                ? null
                : packagingDate.plusMonths(DEFAULT_SHELF_LIFE_MONTHS).format(BEST_BEFORE_FORMATTER);
    }

    private List<String> marketingClaims(Set<LabelClaimType> claimTypes) {
        List<String> claims = new ArrayList<>();

        if (claimTypes == null) {
            return claims;
        }

        for (LabelClaimType claimType : claimTypes) {
            String claim = MARKETING_CLAIMS.get(claimType);
            if (claim != null) {
                claims.add(claim);
            }
        }

        return claims;
    }

    private List<LabelValidationIssueDto> validateLabel(LabelContent labelContent) {
        List<LabelValidationIssueDto> issues = new ArrayList<>();

        validateNotNull(issues, labelContent.getLotId(), "lotId", "Lot filtre absent");
        validateNotNull(issues, labelContent.getPackagingId(), "packagingId", "Packaging absent");
        validateNotNull(issues, labelContent.getOperatorId(), "operatorId", "Utilisateur courant introuvable");
        validateNotBlank(issues, labelContent.getLotNumber(), "lotNumber", "Numero de lot indisponible");
        validateNotBlank(issues, labelContent.getLegalDenomination(), "legalDenomination",
                "Denomination legale non mappee");
        validateNotBlank(issues, labelContent.getNetQuantity(), "netQuantity", "Quantite packaging absente");
        validateNotBlank(issues, labelContent.getBestBeforeDate(), "bestBeforeDate", "DDM non calculable");
        validateNotBlank(issues, labelContent.getResponsibleName(), "responsibleName", "Responsable non resolu");
        validateNotBlank(issues, labelContent.getResponsibleAddress(), "responsibleAddress",
                "Adresse responsable indisponible");

        // Proof validation for marketing claims removed as per user request

        return issues;
    }

    private boolean applyUpdate(LabelContent labelContent, LabelContentUpdateRequestDto request) {
        boolean updated = false;

        if (request.getLanguage() != null && request.getLanguage() != labelContent.getLanguage()) {
            labelContent.setLanguage(request.getLanguage());
            updated = true;
        }

        if (request.getPackagingDate() != null && !request.getPackagingDate().equals(labelContent.getPackagingDate())) {
            labelContent.setPackagingDate(request.getPackagingDate());
            labelContent.setBestBeforeDate(expiryDate(request.getPackagingDate()));
            updated = true;
        }

        if (applyRequestQualityAndVariety(
                labelContent,
                request.getQualityGrade(),
                request.getVariety()
        )) {
            updated = true;
        }

        if (request.getLegalDenomination() != null) {
            String value = clean(request.getLegalDenomination());
            if (!Objects.equals(value, labelContent.getLegalDenomination())) {
                labelContent.setLegalDenomination(value);
                updated = true;
            }
        }

        if (request.getStorageConditions() != null) {
            String value = clean(request.getStorageConditions());
            if (!Objects.equals(value, labelContent.getStorageConditions())) {
                labelContent.setStorageConditions(value);
                updated = true;
            }
        }

        if (request.getSensoryProfile() != null) {
            String value = clean(request.getSensoryProfile());
            if (!Objects.equals(value, labelContent.getSensoryProfile())) {
                labelContent.setSensoryProfile(value);
                updated = true;
            }
        }

        if (request.getCertifications() != null) {
            List<String> certifications = normalizeStrings(request.getCertifications());
            if (!certifications.equals(labelContent.getCertifications())) {
                labelContent.setCertifications(certifications);
                updated = true;
            }
        }

        if (request.getClaimTypes() != null) {
            Set<LabelClaimType> claimTypes = normalizeClaimTypes(request.getClaimTypes());
            if (!claimTypes.equals(labelContent.getClaimTypes())) {
                labelContent.setClaimTypes(claimTypes);
                labelContent.setMarketingClaims(marketingClaims(claimTypes));
                updated = true;
            }
        }

        if (request.getMarketingClaims() != null) {
            List<String> marketingClaims = normalizeStrings(request.getMarketingClaims());
            if (!marketingClaims.equals(labelContent.getMarketingClaims())) {
                labelContent.setMarketingClaims(marketingClaims);
                updated = true;
            }
        }

        if (request.getLotNumber() != null) {
            String value = clean(request.getLotNumber());
            if (!Objects.equals(value, labelContent.getLotNumber())) {
                labelContent.setLotNumber(value);
                updated = true;
            }
        }

        if (request.getOriginCountry() != null) {
            String value = clean(request.getOriginCountry());
            if (!Objects.equals(value, labelContent.getOriginCountry())) {
                labelContent.setOriginCountry(value);
                updated = true;
            }
        }

        if (request.getNetQuantity() != null) {
            String value = clean(request.getNetQuantity());
            if (!Objects.equals(value, labelContent.getNetQuantity())) {
                labelContent.setNetQuantity(value);
                updated = true;
            }
        }

        if (request.getResponsibleName() != null) {
            String value = clean(request.getResponsibleName());
            if (!Objects.equals(value, labelContent.getResponsibleName())) {
                labelContent.setResponsibleName(value);
                updated = true;
            }
        }

        if (request.getResponsibleAddress() != null) {
            String value = clean(request.getResponsibleAddress());
            if (!Objects.equals(value, labelContent.getResponsibleAddress())) {
                labelContent.setResponsibleAddress(value);
                updated = true;
            }
        }

        if (request.getExtractionMethod() != null) {
            String value = clean(request.getExtractionMethod());
            if (!Objects.equals(value, labelContent.getExtractionMethod())) {
                labelContent.setExtractionMethod(value);
                updated = true;
            }
        }

        if (request.getBestBeforeDate() != null) {
            String value = clean(request.getBestBeforeDate());
            if (!Objects.equals(value, labelContent.getBestBeforeDate())) {
                labelContent.setBestBeforeDate(value);
                updated = true;
            }
        }

        return updated;
    }

    private String exportJson(LabelContent labelContent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("legalDenomination", labelContent.getLegalDenomination());
        payload.put("originCountry", labelContent.getOriginCountry());
        payload.put("netQuantity", labelContent.getNetQuantity());
        payload.put("bestBeforeDate", labelContent.getBestBeforeDate());
        payload.put("storageConditions", labelContent.getStorageConditions());
        payload.put("responsibleName", labelContent.getResponsibleName());
        payload.put("responsibleAddress", labelContent.getResponsibleAddress());
        payload.put("lotNumber", labelContent.getLotNumber());
        payload.put("variety", labelContent.getVariety());
        payload.put("qualityGrade", labelContent.getQualityGrade());
        payload.put("extractionMethod", labelContent.getExtractionMethod());
        payload.put("sensoryProfile", labelContent.getSensoryProfile());

        // Merge certifications and marketing claims for the label display
        Set<String> allCerts = new LinkedHashSet<>();
        if (labelContent.getCertifications() != null)
            allCerts.addAll(labelContent.getCertifications());
        if (labelContent.getMarketingClaims() != null)
            allCerts.addAll(labelContent.getMarketingClaims());
        payload.put("certifications", new ArrayList<>(allCerts));

        payload.put("marketingClaims", labelContent.getMarketingClaims());
        payload.put("status", labelContent.getStatus().name());
        payload.put("publicCode", labelContent.getQrHex());

        // Enrich certifications with logos
        List<Map<String, String>> certDetails = new ArrayList<>();
        if (labelContent.getCertifications() != null) {
            for (String certName : labelContent.getCertifications()) {
                certificationRepository.findByNameAndIsDeletedFalse(certName).ifPresent(cert -> {
                    Map<String, String> details = new HashMap<>();
                    details.put("name", cert.getName());
                    details.put("logoData", cert.getLogoData());
                    details.put("logoContentType", cert.getLogoContentType());
                    certDetails.add(details);
                });
            }
        }
        payload.put("certificationsDetail", certDetails);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de serialiser l'etiquette en JSON", e);
        }
    }

    private void defaultLegalDenomination(LabelContent labelContent) {
        if (isBlank(labelContent.getLegalDenomination())) {
            labelContent.setLegalDenomination(OFFICIAL_NAMES.get(QualityGrades.OTHER));
        }
    }

    private void validateNotNull(List<LabelValidationIssueDto> issues, Object value, String field, String message) {
        if (value == null) {
            issues.add(new LabelValidationIssueDto(field, message, true));
        }
    }

    private void validateNotBlank(List<LabelValidationIssueDto> issues, String value, String field, String message) {
        if (isBlank(value)) {
            issues.add(new LabelValidationIssueDto(field, message, true));
        }
    }

    private boolean claimSupported(LabelContent labelContent, LabelClaimType claimType) {
        switch (claimType) {
            case MADE_IN_TUNISIA:
                return DEFAULT_ORIGIN_COUNTRY.equalsIgnoreCase(
                        Optional.ofNullable(labelContent.getOriginCountry()).orElse(""));

            case BIO:
                if (labelContent.getCertifications() == null)
                    return false;
                List<String> bioKeys = List.of("BIO", "BIOLOGIQUE", "ORGANIC", "ECOCERT");
                return labelContent.getCertifications().stream()
                        .anyMatch(name -> bioKeys.stream().anyMatch(key -> name.toUpperCase().contains(key)));

            case COLD_EXTRACTION:
                return Optional.ofNullable(labelContent.getExtractionMethod())
                        .orElse("")
                        .toLowerCase(Locale.ROOT)
                        .contains("froid");

            case PRIVATE_LABEL:
                return !isBlank(labelContent.getResponsibleName());

            case OTHER:
            default:
                return false;
        }
    }

    private UserContext fetchCurrentUser() {
        Map<String, Object> currentUser = SecurityUtils.getCurrentOsmUser()
                .map(LinkedHashMap::new)
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));

        String rawId = stringValue(currentUser.get("id"));

        if (rawId == null) {
            throw new IllegalStateException("Identifiant utilisateur courant introuvable");
        }

        UUID userId;

        try {
            userId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Identifiant utilisateur courant invalide");
        }

        String username = stringValue(currentUser.get("username"));
        String email = stringValue(currentUser.get("email"));
        String login = username != null ? username : email != null ? email : rawId;

        String displayName = joinNonBlank(
                stringValue(currentUser.get("firstName")),
                stringValue(currentUser.get("lastName")));

        if (isBlank(displayName)) {
            displayName = username != null ? username : email != null ? email : rawId;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();

        for (String key : List.of("id", "username", "firstName", "lastName", "email", "phoneNumber")) {
            String value = stringValue(currentUser.get(key));
            if (value != null) {
                snapshot.put(key, value);
            }
        }

        if (snapshot.isEmpty()) {
            snapshot.putAll(currentUser);
        }

        return new UserContext(userId, login, displayName, snapshot);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : clean(String.valueOf(value));
    }

    private String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned == null) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(cleaned);
        }

        return builder.toString();
    }

    private List<String> normalizeStrings(List<String> values) {
        List<String> result = new ArrayList<>();

        if (values == null) {
            return result;
        }

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned != null && !result.contains(cleaned)) {
                result.add(cleaned);
            }
        }

        return result;
    }

    private Set<LabelClaimType> normalizeClaimTypes(Set<LabelClaimType> claimTypes) {
        Set<LabelClaimType> result = new LinkedHashSet<>();

        if (claimTypes != null) {
            for (LabelClaimType claimType : claimTypes) {
                if (claimType != null) {
                    result.add(claimType);
                }
            }
        }

        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LabelExportDto toExportDto(LabelContent labelContent) {
        LabelExportDto dto = new LabelExportDto();

        dto.setLabelId(labelContent.getId());
        dto.setStatus(labelContent.getStatus());
        dto.setLanguage(labelContent.getLanguage());
        dto.setPackagingDate(labelContent.getPackagingDate());
        dto.setLegalDenomination(labelContent.getLegalDenomination());
        dto.setOriginCountry(labelContent.getOriginCountry());
        dto.setNetQuantity(labelContent.getNetQuantity());
        dto.setBestBeforeDate(labelContent.getBestBeforeDate());
        dto.setStorageConditions(labelContent.getStorageConditions());
        dto.setResponsibleName(labelContent.getResponsibleName());
        dto.setResponsibleAddress(labelContent.getResponsibleAddress());
        dto.setLotNumber(labelContent.getLotNumber());
        dto.setVariety(labelContent.getVariety());
        dto.setQualityGrade(labelContent.getQualityGrade());
        dto.setExtractionMethod(labelContent.getExtractionMethod());
        dto.setSensoryProfile(labelContent.getSensoryProfile());
        dto.setCertifications(new ArrayList<>(safeList(labelContent.getCertifications())));
        dto.setClaimTypes(new LinkedHashSet<>(safeSet(labelContent.getClaimTypes())));
        dto.setMarketingClaims(new ArrayList<>(safeList(labelContent.getMarketingClaims())));
        dto.setFrozen(labelContent.getStatus() == LabelContentStatus.FINALIZED
                && !isBlank(labelContent.getFinalPayloadJson()));
        dto.setPayloadJson(dto.isFrozen() ? labelContent.getFinalPayloadJson() : exportJson(labelContent));
        dto.setFinalizedAt(labelContent.getFinalizedAt());
        dto.setFinalizedBy(labelContent.getFinalizedBy());
        dto.setPublicCode(labelContent.getQrHex());

        return dto;
    }

    private LabelContentDto toDto(LabelContent labelContent, List<LabelValidationIssueDto> validationIssues) {
        LabelContentDto dto = new LabelContentDto();

        dto.setId(labelContent.getId());
        dto.setDeleted(labelContent.getDeleted());
        dto.setExternalId(labelContent.getExternalId());
        dto.setLotId(labelContent.getLotId());
        dto.setProductId(labelContent.getProductId());
        dto.setPackagingId(labelContent.getPackagingId());
        dto.setOperatorId(labelContent.getOperatorId());
        dto.setFiltrationOperationId(labelContent.getFiltrationOperationId());
        dto.setStatus(labelContent.getStatus());
        dto.setLanguage(labelContent.getLanguage());
        dto.setPackagingDate(labelContent.getPackagingDate());
        dto.setLegalDenomination(labelContent.getLegalDenomination());
        dto.setOriginCountry(labelContent.getOriginCountry());
        dto.setNetQuantity(labelContent.getNetQuantity());
        dto.setBestBeforeDate(labelContent.getBestBeforeDate());
        dto.setStorageConditions(labelContent.getStorageConditions());
        dto.setResponsibleName(labelContent.getResponsibleName());
        dto.setResponsibleAddress(labelContent.getResponsibleAddress());
        dto.setLotNumber(labelContent.getLotNumber());
        dto.setVariety(labelContent.getVariety());
        dto.setQualityGrade(labelContent.getQualityGrade());
        dto.setExtractionMethod(labelContent.getExtractionMethod());
        dto.setSensoryProfile(labelContent.getSensoryProfile());
        dto.setCertifications(new ArrayList<>(safeList(labelContent.getCertifications())));
        dto.setClaimTypes(new LinkedHashSet<>(safeSet(labelContent.getClaimTypes())));
        dto.setMarketingClaims(new ArrayList<>(safeList(labelContent.getMarketingClaims())));
        dto.setFinalPayloadJson(labelContent.getFinalPayloadJson());
        dto.setFinalizedAt(labelContent.getFinalizedAt());
        dto.setFinalizedBy(labelContent.getFinalizedBy());
        dto.setPublicCode(labelContent.getQrHex());
        dto.setValidationIssues(validationIssues);

        List<LabelSourceSnapshotDto> sourceSnapshotDtos = new ArrayList<>();

        if (labelContent.getSourceSnapshots() != null) {
            for (LabelSource snapshot : labelContent.getSourceSnapshots()) {
                sourceSnapshotDtos.add(getLabelSourceSnapshotDto(snapshot));
            }
        }

        dto.setSourceSnapshots(sourceSnapshotDtos);

        return dto;
    }

    private static LabelSourceSnapshotDto getLabelSourceSnapshotDto(LabelSource snapshot) {
        LabelSourceSnapshotDto snapshotDto = new LabelSourceSnapshotDto();

        snapshotDto.setId(snapshot.getId());
        snapshotDto.setDeleted(snapshot.getDeleted());
        snapshotDto.setExternalId(snapshot.getExternalId());
        snapshotDto.setSourceType(snapshot.getSourceType());
        snapshotDto.setSourceId(snapshot.getSourceId());
        snapshotDto.setSourceBusinessKey(snapshot.getSourceBusinessKey());
        snapshotDto.setSnapshotJson(snapshot.getSnapshotJson());

        return snapshotDto;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private Set<LabelClaimType> safeSet(Set<LabelClaimType> values) {
        return values == null ? Set.of() : values;
    }
}
