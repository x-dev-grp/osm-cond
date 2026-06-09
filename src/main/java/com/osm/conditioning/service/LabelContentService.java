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
import com.osm.conditioning.expedition.dto.GenealogyDto;
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
        UUID tenantId = TenantContext.getCurrentTenant();
        List<LabelContent> labels = tenantId == null
                ? labelContentRepository.findAllByIsDeletedFalse()
                : labelContentRepository.findAllByTenantIdAndIsDeletedFalse(tenantId);

        return labels.stream()
                .peek(this::ensureTraceabilityLotId)
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
                .peek(this::ensureTraceabilityLotId)
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
        GenealogyDto genealogy = fetchGenealogy(request.getLotId());
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
        labelContent.setTraceabilityLotId(resolveTraceabilityLotId(request.getLotId(), request.getTraceabilityLotId(), genealogy));
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
        applyCompanyIdentity(labelContent, companyProfile);

        applyRequestQualityAndVariety(
                labelContent,
                request.getQualityGrade(),
                request.getVariety()
        );

        saveSourceProofs(labelContent, storageUnit, genealogy, packaging, currentUser, companyProfile, request.getFiltrationOperationId());

        LabelContent saved = ensureQrCode(labelContentRepository.save(labelContent));
        return toDto(saved, validateLabel(saved));
    }

    @Transactional(readOnly = true)
    public LabelContentDto getById(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);
        ensureTraceabilityLotId(labelContent);
        return toDto(labelContent, validateLabel(labelContent));
    }

    @Transactional
    public LabelContentDto update(UUID id, LabelContentUpdateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("La requete de mise a jour est obligatoire");
        }

        LabelContent labelContent = findLabelOrThrow(id);
        ensureTraceabilityLotId(labelContent);

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
        ensureTraceabilityLotId(labelContent);

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
        ensureTraceabilityLotId(labelContent);

        if (labelContent.getStatus() != LabelContentStatus.FINALIZED) {
            throw new IllegalStateException("Seule une etiquette finalisee peut etre exportee");
        }

        return toExportDto(labelContent);
    }

    @Transactional
    public LabelContentDto approve(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);
        ensureTraceabilityLotId(labelContent);

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

    private void applyCompanyIdentity(LabelContent labelContent, CompanyProfileDto companyProfile) {
        if (labelContent == null || companyProfile == null) {
            return;
        }

        labelContent.setResponsibleName(clean(companyProfile.getLegalName()));
        labelContent.setResponsibleAddress(joinNonBlank(
                companyProfile.getAddressLine1(),
                companyProfile.getPostalCode(),
                companyProfile.getCity(),
                companyProfile.getGovernorate(),
                DEFAULT_ORIGIN_COUNTRY
        ));
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
            GenealogyDto genealogy,
            ProduitFinalDto packaging,
            UserContext currentUser,
            CompanyProfileDto companyProfile,
            UUID filtrationOperationId
    ) {
        labelContent.getSourceSnapshots().clear();
        addSnapshot(
                labelContent,
                LabelSourceType.FILTERED_LOT,
                labelContent.getTraceabilityLotId() != null ? labelContent.getTraceabilityLotId() : storageUnit.getId(),
                storageUnit.getLotNumber(),
                buildFilteredLotSnapshot(storageUnit, genealogy, labelContent)
        );
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

    private GenealogyDto fetchGenealogy(UUID lotId) {
        try {
            ApiResponse<GenealogyDto> genealogyResponse = clientProductionStorage.getGenealogy(lotId);
            if (genealogyResponse != null && genealogyResponse.isSuccess()) {
                return genealogyResponse.getData();
            }
        } catch (Exception e) {
            OSMLogger.logException(this.getClass(), "fetchGenealogy", e);
        }
        return null;
    }

    private UUID resolveTraceabilityLotId(UUID lotId, UUID requestedTraceabilityLotId, GenealogyDto genealogy) {
        if (requestedTraceabilityLotId != null) {
            return requestedTraceabilityLotId;
        }
        if (genealogy != null && genealogy.getTraceabilityLotId() != null) {
            return genealogy.getTraceabilityLotId();
        }
        return lotId;
    }

    private void ensureTraceabilityLotId(LabelContent labelContent) {
        if (labelContent == null || labelContent.getTraceabilityLotId() != null || labelContent.getLotId() == null) {
            return;
        }

        GenealogyDto genealogy = fetchGenealogy(labelContent.getLotId());
        UUID resolved = resolveTraceabilityLotId(labelContent.getLotId(), null, genealogy);
        if (resolved != null && !resolved.equals(labelContent.getTraceabilityLotId())) {
            labelContent.setTraceabilityLotId(resolved);
            labelContentRepository.save(labelContent);
        }
    }

    private Map<String, Object> buildFilteredLotSnapshot(
            StorageUnitDto storageUnit,
            GenealogyDto genealogy,
            LabelContent labelContent
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("storageUnitId", storageUnit.getId());
        snapshot.put("storageUnitName", storageUnit.getName());
        snapshot.put("traceabilityLotId", labelContent.getTraceabilityLotId());
        snapshot.put("rootReceptionId", genealogy != null ? genealogy.getRootReceptionId() : null);
        snapshot.put("traceabilitySourceType", genealogy != null ? genealogy.getTraceabilitySourceType() : null);
        snapshot.put("lotNumber", storageUnit.getLotNumber());
        snapshot.put("qualityGrade", storageUnit.getQualityGrade());
        snapshot.put("oilVariety", storageUnit.getOilType());
        snapshot.put("genealogy", genealogy);
        return snapshot;
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
        validateNotNull(issues, labelContent.getTraceabilityLotId(), "traceabilityLotId", "Traceabilite lot absente");
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
        Map<String, String> postFiltrationQualityControls = resolvePostFiltrationQualityControls(labelContent);
        payload.put("postFiltrationQualityControls", postFiltrationQualityControls);
        payload.put("qualityControls", buildStructuredQualityControls(postFiltrationQualityControls));
        payload.put("oilCompositionEstimate", buildOilCompositionEstimate(postFiltrationQualityControls, labelContent.getNetQuantity()));

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

        payload.put("language", labelContent.getLanguage() != null ? labelContent.getLanguage().name() : null);
        payload.put("labelCategory",
                labelContent.getLabelCategory() != null ? labelContent.getLabelCategory().name() : null);
        payload.put("packagingDate",
                labelContent.getPackagingDate() != null ? labelContent.getPackagingDate().toString() : null);
        payload.put("qualityGradeLabel", officialNameFromString(labelContent.getQualityGrade()));
        payload.put("claimTypes", labelContent.getClaimTypes() != null
                ? labelContent.getClaimTypes().stream().map(Enum::name).sorted().toList()
                : List.of());
        payload.put("labelId", labelContent.getId());
        payload.put("lotId", labelContent.getLotId());
        payload.put("packagingId", labelContent.getPackagingId());
        payload.put("traceabilityLotId", labelContent.getTraceabilityLotId());
        payload.put("filtrationOperationId", labelContent.getFiltrationOperationId());
        payload.put("productId", labelContent.getProductId());
        if (labelContent.getFinalizedAt() != null) {
            payload.put("finalizedAt", labelContent.getFinalizedAt().toString());
        }
        payload.put("finalizedBy", labelContent.getFinalizedBy());

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

    private Map<String, String> resolvePostFiltrationQualityControls(LabelContent labelContent) {
        if (labelContent == null || labelContent.getSourceSnapshots() == null) {
            return Map.of();
        }

        for (LabelSource snapshot : labelContent.getSourceSnapshots()) {
            if (snapshot == null
                    || snapshot.getSourceType() != LabelSourceType.FILTERED_LOT
                    || isBlank(snapshot.getSnapshotJson())) {
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(snapshot.getSnapshotJson(), Map.class);

                Map<String, String> direct = toStringMap(parsed.get("filteredQualityControls"));
                if (!direct.isEmpty()) {
                    return direct;
                }

                Object genealogyObj = parsed.get("genealogy");
                if (genealogyObj instanceof Map<?, ?> genealogyMap) {
                    Map<String, String> fromGenealogy = toStringMap(genealogyMap.get("filteredQualityControls"));
                    if (!fromGenealogy.isEmpty()) {
                        return fromGenealogy;
                    }

                    Object filtrationsObj = genealogyMap.get("filtrations");
                    if (filtrationsObj instanceof List<?> filtrations) {
                        for (Object filtrationObj : filtrations) {
                            if (filtrationObj instanceof Map<?, ?> filtrationMap) {
                                Map<String, String> controls = toStringMap(filtrationMap.get("qualityControls"));
                                if (!controls.isEmpty()) {
                                    return controls;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Non-blocking: keep export resilient if snapshot format differs.
            }
        }

        return Map.of();
    }

    private List<Map<String, String>> buildStructuredQualityControls(Map<String, String> controls) {
        if (controls == null || controls.isEmpty()) {
            return List.of();
        }

        List<Map<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : controls.entrySet()) {
            if (isCompositionQcKey(entry.getKey())) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("label", entry.getKey());
            item.put("value", entry.getValue());
            entries.add(item);
        }
        return entries;
    }

    private List<Map<String, Object>> buildOilCompositionEstimate(Map<String, String> controls, String netQuantity) {
        Map<String, Map<String, Object>> measured = new LinkedHashMap<>();

        if (controls != null) {
            for (Map.Entry<String, String> entry : controls.entrySet()) {
                String compositionKey = resolveCompositionKey(entry.getKey());
                if (compositionKey == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", compositionKey);
                item.put("label", compositionLabel(compositionKey));
                item.put("value", entry.getValue());
                item.put("per100ml", entry.getValue());
                item.put("source", "measured");
                measured.put(compositionKey, item);
            }
        }

        List<Map<String, Object>> estimate = new ArrayList<>();
        for (String key : List.of(
                "lipides",
                "acides_gras_satures",
                "acides_gras_mono",
                "acides_gras_poly",
                "vitamine_e",
                "energie",
                "glucides",
                "proteines",
                "sel")) {
            if (measured.containsKey(key)) {
                estimate.add(measured.get(key));
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", compositionLabel(key));
            item.put("value", referenceCompositionValue(key, netQuantity));
            item.put("per100ml", referenceCompositionPer100ml(key));
            item.put("source", "estimated");
            estimate.add(item);
        }

        for (Map.Entry<String, Map<String, Object>> entry : measured.entrySet()) {
            String measuredKey = entry.getKey();
            if (estimate.stream().noneMatch(item -> measuredKey.equals(item.get("key")))) {
                estimate.add(entry.getValue());
            }
        }

        return estimate;
    }

    private boolean isCompositionQcKey(String key) {
        return resolveCompositionKey(key) != null;
    }

    private String resolveCompositionKey(String key) {
        if (isBlank(key)) {
            return null;
        }

        String normalized = normalizeQcKey(key);
        if (containsAny(normalized, "lipide", "lipid", "gras", "fat", "matiere grasse")) {
            return "lipides";
        }
        if (containsAny(normalized, "sature", "saturated", "ag sature")) {
            return "acides_gras_satures";
        }
        if (containsAny(normalized, "monoinsature", "monounsaturated", "ag mono")) {
            return "acides_gras_mono";
        }
        if (containsAny(normalized, "polyinsature", "polyunsaturated", "ag poly")) {
            return "acides_gras_poly";
        }
        if (containsAny(normalized, "vitamine e", "vitamin e", "tocopherol")) {
            return "vitamine_e";
        }
        if (containsAny(normalized, "vitamine a", "vitamin a", "retinol")) {
            return "vitamine_a";
        }
        if (containsAny(normalized, "vitamine d", "vitamin d")) {
            return "vitamine_d";
        }
        if (containsAny(normalized, "vitamine k", "vitamin k")) {
            return "vitamine_k";
        }
        if (containsAny(normalized, "polyphenol", "poly phenol")) {
            return "polyphenols";
        }
        if (containsAny(normalized, "energie", "energy", "calorie", "kcal", "kj")) {
            return "energie";
        }
        if (containsAny(normalized, "glucide", "carbohydrate", "carb")) {
            return "glucides";
        }
        if (containsAny(normalized, "proteine", "protein")) {
            return "proteines";
        }
        if (containsAny(normalized, "sel", "sodium", "salt")) {
            return "sel";
        }
        return null;
    }

    private String compositionLabel(String key) {
        return switch (key) {
            case "lipides" -> "Lipides";
            case "acides_gras_satures" -> "Acides gras saturés";
            case "acides_gras_mono" -> "Acides gras monoinsaturés";
            case "acides_gras_poly" -> "Acides gras polyinsaturés";
            case "vitamine_e" -> "Vitamine E";
            case "vitamine_a" -> "Vitamine A";
            case "vitamine_d" -> "Vitamine D";
            case "vitamine_k" -> "Vitamine K";
            case "polyphenols" -> "Polyphénols";
            case "energie" -> "Énergie";
            case "glucides" -> "Glucides";
            case "proteines" -> "Protéines";
            case "sel" -> "Sel";
            default -> key;
        };
    }

    private String referenceCompositionPer100ml(String key) {
        return switch (key) {
            case "lipides" -> "100 g";
            case "acides_gras_satures" -> "14 g";
            case "acides_gras_mono" -> "73 g";
            case "acides_gras_poly" -> "11 g";
            case "vitamine_e" -> "14 mg";
            case "energie" -> "824 kcal / 3389 kJ";
            case "glucides", "proteines", "sel" -> "0 g";
            default -> "-";
        };
    }

    private String referenceCompositionValue(String key, String netQuantity) {
        return referenceCompositionPer100ml(key);
    }

    private String normalizeQcKey(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private boolean containsAny(String normalized, String... tokens) {
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> toStringMap(Object source) {
        if (!(source instanceof Map<?, ?> sourceMap) || sourceMap.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                result.put(key, value);
            }
        }

        return result;
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
        dto.setTraceabilityLotId(labelContent.getTraceabilityLotId());
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
