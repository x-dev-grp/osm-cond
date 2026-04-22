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
import com.xdev.xdevbase.qr.model.QrResolveResponse;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OFService extends BaseServiceImpl<OrdreFabrication, OrdreFabricationDto, OrdreFabricationDto> {

    @Autowired
    private OrdreFabricationRepository ofRepository;

    @Autowired
    private clientInventaire clientInventaire;
    public OFService(BaseRepository<OrdreFabrication> repository,
                     CodeGenerator codeGenerator,
                     ModelMapper modelMapper
    ) {
        super(repository, codeGenerator, modelMapper);
    }

    @Override
    public Class<OrdreFabricationDto> getOutDTOClass() {
        return OrdreFabricationDto.class;
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
    protected Object getData(OrdreFabrication entity) {
        return modelMapper.map(entity, outDTOClass);
    }

    @Override
    public OrdreFabricationDto findById(UUID id) {
        OrdreFabrication of = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found with this id " + id));
        return convertToDto(of);
    }

    @Override
    public List<OrdreFabricationDto> findAll() {
        UUID tenantId = TenantContext.getCurrentTenant();
        return repository.findAllByTenantIdAndIsDeletedFalse(tenantId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrdreFabricationDto creerOF(OrdreFabricationDto dto) {
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
        BigDecimal quantiteCible = dto.getQuantiteCible();
        List<String> ruptures = new ArrayList<>();

        for (BomLineDto line : bom.getLines()) {
            UUID articleId = line.getArticleId();
            BigDecimal besoin = line.getQuantity().multiply(quantiteCible);

            StockSecDto stock;
            try {
                stock = clientInventaire.getStockByArticle(articleId);
            } catch (Exception e) {
                throw new RuntimeException("Impossible de récupérer le stock pour l'article : " + articleId, e);
            }

            Integer quantiteDisponible = (stock != null && stock.getQuantiteActuelle() != null) ? stock.getQuantiteActuelle() : 0;

            if (quantiteDisponible < besoin.intValue()) {
                ruptures.add(String.format("Article %s : besoin = %d, disponible = %d",
                        articleId, besoin.intValue(), quantiteDisponible));
            }
        }

        if (!ruptures.isEmpty()) {
            throw new RuntimeException("Stock insuffisant pour créer l'OF : " + String.join(" ; ", ruptures));
        }
        OrdreFabrication of = new OrdreFabrication();
        of.setCode(generateCode());
        of.setSkuId(dto.getSkuId());
        of.setBomId(bom.getId());
        of.setLigneId(dto.getLigneId());
        of.setLotVracId(dto.getLotVracId());
        of.setQuantiteCible(quantiteCible);
        of.setDateDebutPrevue(dto.getDateDebutPrevue());
        of.setDateFinPrevue(dto.getDateFinPrevue());
        of.setStatut(StatutOF.PLANIFIE);

        for (BomLineDto lineBOMDto : bom.getLines()) {
            LigneOF ligneOF = new LigneOF();
            ligneOF.setOf(of);
            ligneOF.setArticleId(lineBOMDto.getArticleId());
            BigDecimal qteTheorique = lineBOMDto.getQuantity().multiply(quantiteCible);
            ligneOF.setQuantiteTheorique(qteTheorique);
            of.getLignes().add(ligneOF);
        }

        OrdreFabrication saved = ofRepository.save(of);

        // Génération du QR
        QrCodeInfo qrInfo = generateQrInfo(saved.getId());
        OrdreFabricationDto result = convertToDto(saved);
        result.setPublicCode(qrInfo.getPublicCode());
        result.setQrImageBase64(qrInfo.getQrImageBase64());
        return result;
    }
    @Transactional
    public OrdreFabricationDto demarrerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.PLANIFIE && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Impossible de démarrer un OF avec le statut : " + of.getStatut());
        }

        of.setDateDebutReelle(LocalDateTime.now());
        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto mettreEnPause(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS ) {
            throw new RuntimeException("Seul un OF en cours peut être mis en pause");
        }

        of.setStatut(StatutOF.EN_PAUSE);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto reprendreOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en pause peut être repris");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de reprendre un OF bloqué par la qualité");
        }

        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto cloturerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en cours ou en pause peut être clôturé");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de clôturer un OF bloqué. Veuillez d'abord résoudre les problèmes qualité.");
        }
        for (LigneOF ligne : of.getLignes()) {
            BigDecimal quantiteConsommee = (ligne.getQuantiteReelle() != null) ? ligne.getQuantiteReelle() : ligne.getQuantiteTheorique();

            if (quantiteConsommee != null && quantiteConsommee.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("quantite", quantiteConsommee.intValue());
                    payload.put("motif", "Consommation OF " + of.getCode());
                    clientInventaire.sortieStock(ligne.getArticleId(), payload);
                } catch (Exception e) {
                    throw new RuntimeException("Erreur lors de la sortie de stock pour l'article " + ligne.getArticleId() + " : " + e.getMessage(), e);
                }
            }
        }
        of.setDateFinReelle(LocalDateTime.now());
        if (of.getDateDebutReelle() != null) {
            long duree = ChronoUnit.MINUTES.between(of.getDateDebutReelle(), of.getDateFinReelle());
            of.setDureeReelle(duree);
        }
        of.setStatut(StatutOF.CLOTURE);

        OrdreFabrication saved = ofRepository.save(of);
        return convertToDto(saved);
    }
    @Transactional
    public OrdreFabricationDto saisirProduction(UUID id, SaisieProductionDto dto) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvé avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("La saisie de production n'est possible que pour un OF en cours");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("La saisie de production n'est possible pour  un OF bloqué par la qualité");
        }

        if (dto.getQuantiteNC() != null && dto.getQuantiteNC().compareTo(BigDecimal.ZERO) > 0) {
            if (dto.getMotifNC() == null || dto.getMotifNC().trim().isEmpty()) {
                throw new RuntimeException("Le motif est obligatoire pour les produits non conformes (NC)");
            }
            if (of.getMotifNC() != null && !of.getMotifNC().isEmpty()) {
                 of.setMotifNC(of.getMotifNC() + " | " + dto.getMotifNC() + " (" + dto.getQuantiteNC() + ")");
             } else {
                 of.setMotifNC(dto.getMotifNC() + " (" + dto.getQuantiteNC() + ")");
             }
        }

        of.setQuantiteBonne(of.getQuantiteBonne().add(dto.getQuantiteBonne()));
        of.setQuantiteNC(of.getQuantiteNC().add(dto.getQuantiteNC()));

        return convertToDto(ofRepository.save(of));
    }
    @Transactional
    public OrdreFabricationDto ajusterConsommation(UUID id, AjustementConsommationDto ajustement) {
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

    private OrdreFabricationDto convertToDto(OrdreFabrication of) {
        OrdreFabricationDto dto = modelMapper.map(of, OrdreFabricationDto.class);
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

        dto.setPublicCode(of.getQrHex());
        dto.setQrUrl(getQrUrlForPublicCode(of.getQrHex()));
        dto.setQrImageBase64(of.getQrImageBase64());


        return dto;
    }






    private String generateCode() {
        return "OF-" + System.currentTimeMillis();
    }



    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        OrdreFabrication entity = ofRepository.findByQrHex(publicCode)
                .orElseThrow(() -> new EntityNotFoundException("OF non trouvé pour le code : " + publicCode));

        QrResolveResponse response = new QrResolveResponse();
        response.setEntityType("OF");
        response.setPublicCode(publicCode);
        response.setEntityId(entity.getId().toString());
        response.setLabel(entity.getCode());          // le label = numéro OF
        response.setStatus(entity.getStatut().name());
        response.setMobileRoute("/of/detail");
        response.setData(convertToDto(entity));
        return response;
    }
}