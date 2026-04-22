package com.osm.production.service;

import com.osm.production.Enum.QualityStatus;
import com.osm.production.Enum.StatutOF;
import com.osm.production.client.clientInventaire;
import com.osm.production.dto.*;
import com.osm.production.model.LigneOF;
import com.osm.production.model.OrdreFabrication;
import com.osm.production.repository.OrdreFabricationRepository;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.model.QrCodeInfo;
import com.xdev.xdevbase.repos.BaseRepository;
import com.xdev.xdevbase.services.impl.BaseServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OFService extends BaseServiceImpl<OrdreFabrication, OrdreFabricationtDto, OrdreFabricationtDto> {

    @Autowired
    private OrdreFabricationRepository ofRepository;

    @Autowired
    private clientInventaire clientInventaire;

    // Constructeur aligné avec BaseServiceImpl
    public OFService(BaseRepository<OrdreFabrication> repository,
                     CodeGenerator codeGenerator,
                     ModelMapper modelMapper
                     ) {
        super(repository, codeGenerator, modelMapper);
    }

    @Override
    public Class<OrdreFabricationtDto> getOutDTOClass() {
        return OrdreFabricationtDto.class;
    }


    @Transactional
    public OrdreFabricationtDto creerOF(OrdreFabricationtDto dto) {
        SKUDto sku = clientInventaire.getSkuById(dto.getSkuId());
        if (sku == null) {
            throw new RuntimeException("SKU non trouvé avec l'id : " + dto.getSkuId());
        }
        if (dto.getBomId() == null) {
            throw new RuntimeException("L'identifiant de la BOM est obligatoire");
        }
        BOMDto bom = clientInventaire.getBomById(dto.getBomId());
        if (bom == null) {
            throw new RuntimeException("BOM non trouvée avec l'id : " + dto.getBomId());
        }
        if (!bom.getSkuId().equals(dto.getSkuId())) {
            throw new RuntimeException("La BOM sélectionnée ne correspond pas au SKU");
        }
        if (dto.getLigneId() != null) {
            LigneConditionnementDto ligne = clientInventaire.getLigneById(dto.getLigneId());
            if (ligne == null) {
                throw new RuntimeException("Ligne non trouvée avec l'id : " + dto.getLigneId());
            }
        }

        OrdreFabrication of = new OrdreFabrication();
        of.setCode(generateCode());
        of.setSkuId(dto.getSkuId());
        of.setBomId(bom.getId());
        of.setLigneId(dto.getLigneId());
        of.setLotVracId(dto.getLotVracId());
        of.setQuantiteCible(dto.getQuantiteCible());
        of.setDateDebutPrevue(dto.getDateDebutPrevue());
        of.setDateFinPrevue(dto.getDateFinPrevue());
        of.setStatut(StatutOF.PLANIFIE);
        for (BomLineDto lineBOMDto : bom.getLines()) {
            LigneOF ligneOF = new LigneOF();
            ligneOF.setOf(of);
            ligneOF.setArticleId(lineBOMDto.getArticleId());
            BigDecimal qteTheorique = lineBOMDto.getQuantity().multiply(dto.getQuantiteCible());
            ligneOF.setQuantiteTheorique(qteTheorique);
            of.getLignes().add(ligneOF);
        }

        OrdreFabrication saved = ofRepository.save(of);

        // Génération du QR après sauvegarde
        QrCodeInfo qrInfo = generateQrInfo(saved.getId());

        // Construction du DTO de réponse avec les infos QR
        OrdreFabricationtDto result = convertToDto(saved);
        result.setPublicCode(qrInfo.getPublicCode());
        result.setQrImageBase64(qrInfo.getQrImageBase64());
        return result;
    }
    @Transactional
    public OrdreFabricationtDto demarrerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.PLANIFIE && of.getStatut() != StatutOF.EN_PAUSE ) {
            throw new RuntimeException("Impossible de démarrer un OF avec le statut : " + of.getStatut());
        }

        of.setDateDebutReelle(LocalDateTime.now());
        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationtDto mettreEnPause(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("Seul un OF en cours peut être mis en pause");
        }

        of.setStatut(StatutOF.EN_PAUSE);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationtDto reprendreOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en pause peut être repris");
        }

        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationtDto cloturerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en cours ou en pause peut être clôturé");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de clôturer un OF bloqué. Veuillez d'abord résoudre les problèmes qualité.");
        }

        of.setDateFinReelle(LocalDateTime.now());
        if (of.getDateDebutReelle() != null) {
            long duree = ChronoUnit.MINUTES.between(of.getDateDebutReelle(), of.getDateFinReelle());
            of.setDureeReelle(duree);
        }

        of.setStatut(StatutOF.CLOTURE);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationtDto saisirProduction(UUID id, SaisieProductionDto dto) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("La saisie de production n'est possible que pour un OF en cours");
        }

        of.setQuantiteBonne(of.getQuantiteBonne().add(dto.getQuantiteBonne()));
        of.setQuantiteNC(of.getQuantiteNC().add(dto.getQuantiteNC()));

        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationtDto ajusterConsommation(UUID id, AjustementConsommationDto ajustement) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        LigneOF ligne = of.getLignes().stream()
                .filter(l -> l.getArticleId().equals(ajustement.getArticleId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Article non trouvé dans l'OF : " + ajustement.getArticleId()));

        ligne.setQuantiteReelle(ajustement.getQuantiteReelle());
        ligne.setMotifAjustement(ajustement.getMotif());

        return convertToDto(ofRepository.save(of));
    }

    private OrdreFabricationtDto convertToDto(OrdreFabrication of) {
        OrdreFabricationtDto dto = new OrdreFabricationtDto();
        dto.setId(of.getId());
        dto.setCode(of.getCode());
        dto.setStatut(of.getStatut());
        dto.setDateDebutPrevue(of.getDateDebutPrevue());
        dto.setDateFinPrevue(of.getDateFinPrevue());
        dto.setDateDebutReelle(of.getDateDebutReelle());
        dto.setDateFinReelle(of.getDateFinReelle());
        dto.setQuantiteCible(of.getQuantiteCible());
        dto.setQuantiteBonne(of.getQuantiteBonne());
        dto.setQuantiteNC(of.getQuantiteNC());
        dto.setDureeReelle(of.getDureeReelle());
        dto.setSkuId(of.getSkuId());
        dto.setLigneId(of.getLigneId());
        dto.setLotVracId(of.getLotVracId());
        dto.setPublicCode(of.getQrHex());
        dto.setQrImageBase64(of.getQrImageBase64());

        try {
            SKUDto sku = clientInventaire.getSkuById(of.getSkuId());
            dto.setSkuCode(sku.getCode());
        } catch (Exception e) {
            dto.setSkuCode("N/A");
        }

        try {
            LigneConditionnementDto ligne = clientInventaire.getLigneById(of.getLigneId());
            dto.setLigneNom(ligne.getNom());
        } catch (Exception e) {
            dto.setLigneNom("N/A");
        }

        List<LigneOFDto> ligneDtos = of.getLignes().stream().map(ligne -> {
            LigneOFDto l = new LigneOFDto();
            l.setArticleId(ligne.getArticleId());
            try {
                ArticleSecDto article = clientInventaire.getArticleById(ligne.getArticleId());
                l.setArticleNom(article.getNom());
            } catch (Exception e) {
                l.setArticleNom("N/A");
            }
            l.setQuantiteTheorique(ligne.getQuantiteTheorique());
            l.setQuantiteReelle(ligne.getQuantiteReelle());
            l.setMotifAjustement(ligne.getMotifAjustement());
            return l;
        }).collect(Collectors.toList());
        dto.setLignes(ligneDtos);

        return dto;
    }

    @Override
    protected String getEntityType() {
        return "OF";
    }

    @Override
    protected String getLabel(OrdreFabrication entity) {
        return entity.getCode();
    }

    @Override
    protected String getStatus(OrdreFabrication entity) {
        return entity.getStatut().name();
    }

    @Override
    protected String getMobileRoute() {
        return "/of/detail";
    }

    @Override
    protected String getWebRoute(OrdreFabrication entity) {
        return entity != null && entity.getId() != null ? "/of/" + entity.getId() : "/of";
    }

    // Optionally, override getData to return full DTO
    @Override
    protected Object getData(OrdreFabrication entity) {
        return modelMapper.map(entity, outDTOClass);
    }

    @Override
    public OrdreFabricationtDto findById(UUID id) {
        OrdreFabrication of = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found with this id " + id));
        return convertToDto(of);
    }

    @Override
    public List<OrdreFabricationtDto> findAll() {
        UUID tenantId = TenantContext.getCurrentTenant();
        return repository.findAllByTenantIdAndIsDeletedFalse(tenantId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrdreFabricationtDto findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code OF est obligatoire");
        }

        UUID tenantId = TenantContext.getCurrentTenant();
        String normalizedCode = code.trim();
        OrdreFabrication of = ofRepository.findByCodeAndTenantIdAndIsDeletedFalse(normalizedCode, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("OF introuvable pour le code : " + normalizedCode));
        return convertToDto(of);
    }


    private String generateCode() {
        return "OF-" + System.currentTimeMillis();
    }
    @Override
    public Class<OrdreFabricationtDto> getOutDTOClass() {
        return OrdreFabricationtDto.class;
    }
}