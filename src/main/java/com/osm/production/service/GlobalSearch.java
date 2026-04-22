package com.osm.production.service;

import com.osm.production.model.LabelContent;
import com.osm.production.model.LabelSource;
import com.osm.production.model.LigneOF;
import com.osm.production.model.OrdreFabrication;
import com.osm.production.projet.entity.Projet;
import com.osm.production.projet.repository.ProjetRepository;
import com.osm.production.repository.LabelContentRepository;
import com.osm.production.repository.LabelSourceRepository;
import com.osm.production.repository.LigneOFRepository;
import com.osm.production.repository.OrdreFabricationRepository;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.model.QrResolveResponse;
import com.xdev.xdevbase.services.GlobalCodeSearchContributor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class GlobalSearch implements GlobalCodeSearchContributor {

    private final OrdreFabricationRepository ordreFabricationRepository;
    private final LabelContentRepository labelContentRepository;
    private final LigneOFRepository ligneOFRepository;
    private final LabelSourceRepository labelSourceRepository;
    private final ProjetRepository projetRepository;


    public GlobalSearch(
            OrdreFabricationRepository ordreFabricationRepository,
            LabelContentRepository labelContentRepository,
            LigneOFRepository ligneOFRepository,
            LabelSourceRepository labelSourceRepository ,
            ProjetRepository projetRepository
    ) {
        this.ordreFabricationRepository = ordreFabricationRepository;
        this.labelContentRepository = labelContentRepository;
        this.ligneOFRepository = ligneOFRepository;
        this.labelSourceRepository = labelSourceRepository;
        this.projetRepository = projetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QrResolveResponse> searchByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        UUID tenantId = TenantContext.getCurrentTenant();

        Optional<QrResolveResponse> ofMatch = resolveOf(normalized, tenantId);
        if (ofMatch.isPresent()) {
            return ofMatch;
        }

        Optional<QrResolveResponse> projetMatch = resolveProjet(normalized, tenantId);
        if (projetMatch.isPresent()) {
            return projetMatch;
        }

        Optional<QrResolveResponse> labelMatch = resolveLabelContent(normalized, tenantId);
        if (labelMatch.isPresent()) {
            return labelMatch;
        }

        Optional<QrResolveResponse> ligneMatch = resolveLigneOf(normalized, tenantId);
        if (ligneMatch.isPresent()) {
            return ligneMatch;
        }

        return resolveLabelSource(normalized, tenantId);
    }

    private Optional<QrResolveResponse> resolveOf(String code, UUID tenantId) {
        Optional<OrdreFabrication> entity = tenantId == null
                ? ordreFabricationRepository.findByQrHex(code)
                : ordreFabricationRepository.findByQrHexAndTenantIdAndIsDeletedFalse(code, tenantId);

        return entity.map(of -> response(
                "OF",
                code,
                of.getId(),
                of.getCode(),
                of.getStatut() != null ? of.getStatut().name() : null,
                "/of/detail",
                "/of/" + of.getId(),
                null
        ));
    }

    private Optional<QrResolveResponse> resolveLabelContent(String code, UUID tenantId) {
        Optional<LabelContent> entity = tenantId == null
                ? labelContentRepository.findByQrHex(code)
                : labelContentRepository.findByQrHexAndTenantIdAndIsDeletedFalse(code, tenantId);

        return entity.map(label -> response(
                "LABEL_CONTENT",
                code,
                label.getId(),
                label.getLotNumber(),
                label.getStatus() != null ? label.getStatus().name() : null,
                "/labels",
                null,
                null
        ));
    }

    private Optional<QrResolveResponse> resolveLigneOf(String code, UUID tenantId) {
        Optional<LigneOF> entity = tenantId == null
                ? ligneOFRepository.findByQrHex(code)
                : ligneOFRepository.findByQrHexAndTenantIdAndIsDeletedFalse(code, tenantId);

        return entity.map(ligne -> {
            UUID ofId = ligne.getOf() != null ? ligne.getOf().getId() : null;
            return response(
                    "LIGNE_OF",
                    code,
                    ligne.getId(),
                    ofId != null ? "Ligne OF " + ofId : "Ligne OF",
                    null,
                    "/of/detail",
                    null,
                    null
            );
        });
    }

    private Optional<QrResolveResponse> resolveLabelSource(String code, UUID tenantId) {
        Optional<LabelSource> entity = tenantId == null
                ? labelSourceRepository.findByQrHex(code)
                : labelSourceRepository.findByQrHexAndTenantIdAndIsDeletedFalse(code, tenantId);

        return entity.map(source -> {
            return response(
                    "LABEL_SOURCE",
                    code,
                    source.getId(),
                    source.getSourceBusinessKey(),
                    source.getSourceType() != null ? source.getSourceType().name() : null,
                    "/labels",
                    null,
                    null
            );
        });
    }


    private Optional<QrResolveResponse> resolveProjet(String code, UUID tenantId) {
        // 1. Chercher par qrHex
        Optional<Projet> entity = tenantId == null
                ? projetRepository.findByQrHex(code)
                : projetRepository.findByQrHexAndTenantIdAndIsDeletedFalse(code, tenantId);

        // 2. Fallback sur le business code si non trouvé par qrHex
        if (entity.isEmpty()) {
            entity = tenantId == null
                    ? projetRepository.findByCodeAndIsDeletedFalse(code)
                    : projetRepository.findByCodeAndTenantIdAndIsDeletedFalse(code, tenantId);
        }

        return entity.map(projet -> response(
                "PROJET",
                code,
                projet.getId(),
                projet.getCode() != null ? projet.getCode() : "Projet " + projet.getClient().getNom(),
                projet.getStatut(),
                "/projets/detail",
                "/projets/detail/" + projet.getId(),
                null
        ));
    }





    private QrResolveResponse response(
            String entityType,
            String code,
            UUID entityId,
            String label,
            String status,
            String mobileRoute,
            String webRoute,
            Object data
    ) {
        QrResolveResponse response = new QrResolveResponse();
        response.setEntityType(entityType);
        response.setPublicCode(code);
        response.setEntityId(entityId != null ? entityId.toString() : null);
        response.setLabel(label);
        response.setStatus(status);
        response.setMobileRoute(mobileRoute);
        response.setWebRoute(webRoute);
        response.setData(data);
        return response;
    }
}
