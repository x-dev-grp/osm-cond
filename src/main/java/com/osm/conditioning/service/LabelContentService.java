package com.osm.conditioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionDelivery;
import com.osm.conditioning.client.clientProductionStorage;
import com.osm.conditioning.client.clientSecurityCompanyProfile;
import com.osm.conditioning.dto.SKUDto;
import com.osm.conditioning.model.LabelContent;
import com.osm.conditioning.model.LabelSource;
import com.osm.conditioning.repository.LabelContentRepository;
import com.xdev.communicator.models.enums.*;
import com.xdev.communicator.models.shared.*;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
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
            QualityGrades.OTHER, "Huile d'olive"
    );
    private static final Map<LabelClaimType, String> MARKETING_CLAIMS = Map.of(
            LabelClaimType.MADE_IN_TUNISIA, "Made in Tunisia",
            LabelClaimType.BIO, "BIO",
            LabelClaimType.COLD_EXTRACTION, "Extraction a froid",
            LabelClaimType.PRIVATE_LABEL, "Private Label",
            LabelClaimType.OTHER, "Autre claim"
    );

    private final LabelContentRepository labelContentRepository;
    private final clientInventaire clientInventaire;
    private final clientProductionStorage clientProductionStorage;
    private final clientProductionDelivery clientProductionDelivery;
    private final clientSecurityCompanyProfile clientSecurityCompanyProfile;
    private final ObjectMapper objectMapper;
    private final CodeGenerator codeGenerator;

    public LabelContentService(
            LabelContentRepository labelContentRepository,
            clientInventaire clientInventaire,
            clientProductionStorage clientProductionStorage,
            clientProductionDelivery clientProductionDelivery,
            clientSecurityCompanyProfile clientSecurityCompanyProfile,
            ObjectMapper objectMapper,
            CodeGenerator codeGenerator
    ) {
        this.labelContentRepository = labelContentRepository;
        this.clientInventaire = clientInventaire;
        this.clientProductionStorage = clientProductionStorage;
        this.clientProductionDelivery = clientProductionDelivery;
        this.clientSecurityCompanyProfile = clientSecurityCompanyProfile;
        this.objectMapper = objectMapper;
        this.codeGenerator = codeGenerator;
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

    //Récupère les informations
    @Transactional
    public LabelContentDto generate(LabelGenerateRequestDto request) {
        UserContext currentUser = fetchCurrentUser();
        ApiResponse<StorageUnitDto> response = clientProductionStorage.getStorageUnit(request.getLotId());
        if (response == null || response.getData() == null) {
            throw new EntityNotFoundException("Lot filtre introuvable");
        }
        StorageUnitDto storageUnit = response.getData();
        SKUDto packaging = clientInventaire.getSkuById(request.getPackagingId());
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return null;
        }
        CompanyProfileDto companyProfile = clientSecurityCompanyProfile.getByTenantId(tenantId);

        LabelContent labelContent = new LabelContent();
        labelContent.setLotId(request.getLotId());
        labelContent.setPackagingId(request.getPackagingId());
        labelContent.setOperatorId(currentUser.id());
        labelContent.setLanguage(Optional.ofNullable(request.getLanguage()).orElse(LabelLanguage.FR));
        labelContent.setPackagingDate(Optional.ofNullable(request.getPackagingDate()).orElse(LocalDate.now()));
        labelContent.setLabelCategory(request.getLabelCategory());

        prepareLabelContent(labelContent, storageUnit, packaging, companyProfile);
        saveSourceProofs(labelContent, storageUnit, packaging, currentUser, companyProfile);

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

        //cherche l'etiquette
        LabelContent labelContent = findLabelOrThrow(id);

        if (labelContent.getStatus() == LabelContentStatus.FINALIZED) {
            throw new IllegalStateException("Le contenu finalise ne peut plus etre modifie");
        }

        boolean updated = applyUpdate(labelContent, request);

        if (updated && labelContent.getStatus() == LabelContentStatus.VALIDATED) {
            labelContent.setStatus(LabelContentStatus.DRAFT);
        }
        labelContent.setFinalPayloadJson(null);

        //sauv dans la base
        LabelContent saved = labelContentRepository.save(labelContent);
        return toDto(saved, validateLabel(saved));
    }

    @Transactional(readOnly = true)
    public LabelExportDto export(UUID id) {
        return toExportDto(findLabelOrThrow(id));
    }

    @Transactional
    public LabelContentDto validate(UUID id) {
        LabelContent labelContent = findLabelOrThrow(id);
        defaultLegalDenomination(labelContent);
        List<LabelValidationIssueDto> issues = validateLabel(labelContent);
        if (labelContent.getStatus() != LabelContentStatus.FINALIZED) {
            labelContent.setStatus(issues.isEmpty() ? LabelContentStatus.VALIDATED : LabelContentStatus.DRAFT);
        }
        return toDto(labelContentRepository.save(labelContent), issues);
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
        labelContent.setFinalPayloadJson(ExportJson(labelContent));
        return toDto(labelContentRepository.save(labelContent), List.of());
    }

    //l'etiquette existe , n'est pas supp , si problem
    private LabelContent findLabelOrThrow(UUID id) {
        return labelContentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Label content introuvable pour l'id: " + id));
    }

    private LabelContent ensureQrCode(LabelContent labelContent) {
        if (!isBlank(labelContent.getQrHex())) {
            return labelContent;
        }
        labelContent.setQrHex(codeGenerator.generateUnique(labelContentRepository::existsByQrHex));
        return labelContentRepository.save(labelContent);
    }

    //cherch les carateristique
    private SKUDto loadPackaging(UUID packagingId) {
        try {

        } catch (Exception ignored) {
        }
        throw new EntityNotFoundException("Packaging introuvable pour l'id: " + packagingId);
    }


    //prend une etiquette vide et remplit auto avec tt les info
    private void prepareLabelContent(LabelContent labelContent, StorageUnitDto storageUnit, SKUDto packaging, CompanyProfileDto companyProfile) {
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
        labelContent.setResponsibleAddress(companyProfile == null ? null : joinNonBlank(
                companyProfile.getAddressLine1(),
                companyProfile.getPostalCode(),
                companyProfile.getCity(),
                companyProfile.getGovernorate(),
                DEFAULT_ORIGIN_COUNTRY
        ));
        labelContent.setExtractionMethod(Olive_Oil_Type.OB.getName());
        labelContent.setSensoryProfile("Profil issu du controle qualite");
        labelContent.setClaimTypes(new LinkedHashSet<>());
        labelContent.setMarketingClaims(new ArrayList<>());
        labelContent.setCertifications(new ArrayList<>());
    }

    //approuver d'ou vienne les infos
    private void saveSourceProofs(LabelContent labelContent, StorageUnitDto storageUnit, SKUDto packaging, UserContext currentUser, CompanyProfileDto companyProfile) {
        labelContent.getSourceSnapshots().clear();
        addSnapshot(labelContent, LabelSourceType.FILTERED_LOT, storageUnit.getId(), storageUnit.getLotNumber(), storageUnit);
        addSnapshot(labelContent, LabelSourceType.PACKAGING, packaging.getId(), packaging.getCode(), packaging);
        addSnapshot(labelContent, LabelSourceType.OPERATOR, currentUser.id(), currentUser.displayName(), currentUser.snapshot());
        if (companyProfile != null) {
            addSnapshot(labelContent, LabelSourceType.COMPANY_PROFILE, companyProfile.getId(), companyProfile.getLegalName(), companyProfile);
        }

    }

    //cree chaque preuve individuelle
    private void addSnapshot(LabelContent labelContent, LabelSourceType type, UUID sourceId, String businessKey, Object payload) {
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

    //Transformer une information sur la qualité de l'huile d’olive en dénomination légale officielle
    // telle qu’elle doit apparaître sur l’étiquette
    private String officialName(QualityGrades qualityGrade) {
        if (qualityGrade != null) {
            return Optional.ofNullable(OFFICIAL_NAMES.get(qualityGrade))
                    .orElse(OFFICIAL_NAMES.get(QualityGrades.OTHER));
        }

        String category = "";
        String normalized = "";
        if (qualityGrade != null) {
            normalized = Normalizer.normalize(qualityGrade.toString(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toUpperCase(Locale.ROOT)
                    .replace("'", "")
                    .replace("-", "")
                    .replace("_", "")
                    .replace(" ", "");

        } else {
            normalized = "EXTRAVIRGIN";
        }

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
        if (normalized.contains("OLIVEOIL") || normalized.contains("HUILEDOLIVE")) {
            return OFFICIAL_NAMES.get(QualityGrades.OTHER);
        }
        return category;
    }

    //ML et L
    private String formatVolume(Float volume) {
        if (volume == null || volume <= 0) {
            return null;
        }
        if (volume >= 1000f) {
            return volume % 1000f == 0  //f signifie que c'est un nombre décimal (Float)
                    ? String.format(Locale.ROOT, "%.0f L", volume / 1000f)
                    : String.format(Locale.ROOT, "%.2f L", volume / 1000f);
        }
        return String.format(Locale.ROOT, "%.0f ml", volume);
    }

    //retourne la quantite totale sur l'etiquette
    private String calculateQuantity(SKUDto packaging, LabelCategory category) {
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

    //date limite de consommation
    private String expiryDate(LocalDate packagingDate) {
        return packagingDate == null ? null : packagingDate.plusMonths(DEFAULT_SHELF_LIFE_MONTHS).format(BEST_BEFORE_FORMATTER);
    }

    private List<String> marketingClaims(Set<LabelClaimType> claimTypes) {
        List<String> claims = new ArrayList<>();
        for (LabelClaimType claimType : claimTypes) {
            String claim = MARKETING_CLAIMS.get(claimType);
            if (claim != null) {
                claims.add(claim);
            }
        }
        return claims;
    }

    //tchouflk tt les champs valider ouu nn
    private List<LabelValidationIssueDto> validateLabel(LabelContent labelContent) {
        List<LabelValidationIssueDto> issues = new ArrayList<>();
        validateNotNull(issues, labelContent.getLotId(), "lotId", "Lot filtre absent");
        validateNotNull(issues, labelContent.getPackagingId(), "packagingId", "Packaging absent");
        validateNotNull(issues, labelContent.getOperatorId(), "operatorId", "Utilisateur courant introuvable");
        validateNotBlank(issues, labelContent.getLotNumber(), "lotNumber", "Numero de lot indisponible");
        validateNotBlank(issues, labelContent.getLegalDenomination(), "legalDenomination", "Denomination legale non mappee");
        validateNotBlank(issues, labelContent.getNetQuantity(), "netQuantity", "Quantite packaging absente");
        validateNotBlank(issues, labelContent.getBestBeforeDate(), "bestBeforeDate", "DDM non calculable");
        validateNotBlank(issues, labelContent.getResponsibleName(), "responsibleName", "Responsable non resolu");
        validateNotBlank(issues, labelContent.getResponsibleAddress(), "responsibleAddress", "Adresse responsable indisponible");
        for (LabelClaimType claimType : labelContent.getClaimTypes()) {
            if (!claimSupported(labelContent, claimType)) {
                issues.add(new LabelValidationIssueDto("claimTypes", "Le claim " + claimType.name() + " ne dispose pas d'une preuve suffisante", true));
            }
        }
        return issues;
    }

    //les champs quand peut modifier
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
        return updated;
    }

    //transforme tt les champs d'une etiquette en une chaine json pour imprimer
    private String ExportJson(LabelContent labelContent) {
        // 1. Construire la map des données (anciennement payloadData)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("legalDenomination", labelContent.getLegalDenomination());
        payload.put("originCountry", labelContent.getOriginCountry());
        payload.put("netQuantity", labelContent.getNetQuantity());
        payload.put("bestBeforeDate", labelContent.getBestBeforeDate());
        payload.put("storageConditions", labelContent.getStorageConditions());
        payload.put("responsibleName", labelContent.getResponsibleName());
        payload.put("responsibleAddress", labelContent.getResponsibleAddress());
        payload.put("lotNumber", labelContent.getLotNumber());
        payload.put("extractionMethod", labelContent.getExtractionMethod());
        payload.put("sensoryProfile", labelContent.getSensoryProfile());
        payload.put("certifications", labelContent.getCertifications());
        payload.put("marketingClaims", labelContent.getMarketingClaims());
        payload.put("status", labelContent.getStatus().name());
        payload.put("publicCode", labelContent.getQrHex());

        // 2. Convertir la map en JSON (anciennement writeJson)
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser l'étiquette en JSON", e);
        }
    }

    //donne un nom pour l'etiquette si ils absent
    private void defaultLegalDenomination(LabelContent labelContent) {
        if (isBlank(labelContent.getLegalDenomination())) {
            labelContent.setLegalDenomination(OFFICIAL_NAMES.get(QualityGrades.OTHER));
        }
    }

    //verifi systematiquement si un champs nul
    private void validateNotNull(List<LabelValidationIssueDto> issues, Object value, String field, String message) {
        if (value == null) {
            issues.add(new LabelValidationIssueDto(field, message, true));
        }
    }

    //La valeur n’est ni null, ni vide, ni composée uniquement d’espaces
    private void validateNotBlank(List<LabelValidationIssueDto> issues, String value, String field, String message) {
        if (isBlank(value)) {
            issues.add(new LabelValidationIssueDto(field, message, true));
        }
    }

    //verifie les argument de marketing
    private boolean claimSupported(LabelContent labelContent, LabelClaimType claimType) {
        switch (claimType) {
            case MADE_IN_TUNISIA:
                return DEFAULT_ORIGIN_COUNTRY.equalsIgnoreCase(Optional.ofNullable(labelContent.getOriginCountry()).orElse(""));
            case BIO:
                boolean b = false;
                for (String certification : labelContent.getCertifications()) {
                    if ("BIO".equalsIgnoreCase(certification)) {
                        b = true;
                        break;
                    }
                }
                return b;
            case COLD_EXTRACTION:
                return Optional.ofNullable(labelContent.getExtractionMethod()).orElse("").toLowerCase(Locale.ROOT).contains("froid");
            case PRIVATE_LABEL:
                return !isBlank(labelContent.getResponsibleName());
            case OTHER:
            default:
                return false;
        }
    }

    // chercher l’utilisateur actuellement connecté
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

        String displayName = joinNonBlank(stringValue(currentUser.get("firstName")), stringValue(currentUser.get("lastName")));
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

    //supp et netoyer les espace
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

    //transforme l'etiquette interne en un package pret pour l'impression
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
        dto.setExtractionMethod(labelContent.getExtractionMethod());
        dto.setSensoryProfile(labelContent.getSensoryProfile());
        dto.setCertifications(new ArrayList<>(labelContent.getCertifications()));
        dto.setClaimTypes(new LinkedHashSet<>(labelContent.getClaimTypes()));
        dto.setMarketingClaims(new ArrayList<>(labelContent.getMarketingClaims()));
        dto.setFrozen(labelContent.getStatus() == LabelContentStatus.FINALIZED && !isBlank(labelContent.getFinalPayloadJson()));
        dto.setPayloadJson(dto.isFrozen() ? labelContent.getFinalPayloadJson() : ExportJson(labelContent));
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
        dto.setPackagingId(labelContent.getPackagingId());
        dto.setOperatorId(labelContent.getOperatorId());
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
        dto.setExtractionMethod(labelContent.getExtractionMethod());
        dto.setSensoryProfile(labelContent.getSensoryProfile());
        dto.setCertifications(new ArrayList<>(labelContent.getCertifications()));
        dto.setClaimTypes(new LinkedHashSet<>(labelContent.getClaimTypes()));
        dto.setMarketingClaims(new ArrayList<>(labelContent.getMarketingClaims()));
        dto.setFinalPayloadJson(labelContent.getFinalPayloadJson());
        dto.setFinalizedAt(labelContent.getFinalizedAt());
        dto.setFinalizedBy(labelContent.getFinalizedBy());
        dto.setPublicCode(labelContent.getQrHex());
        dto.setValidationIssues(validationIssues);

        List<LabelSourceSnapshotDto> sourceSnapshotDtos = new ArrayList<>();
        for (LabelSource snapshot : labelContent.getSourceSnapshots()) {
            LabelSourceSnapshotDto snapshotDto = getLabelSourceSnapshotDto(snapshot);
            sourceSnapshotDtos.add(snapshotDto);
        }
        dto.setSourceSnapshots(sourceSnapshotDtos);
        return dto;
    }

}
