package com.osm.conditioning.service;

import com.osm.conditioning.Enum.QualityStatus;
import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.dto.*;
import com.osm.conditioning.model.LigneOF;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.StorageUnitDto;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.qr.CodeGenerator;
import com.xdev.xdevbase.qr.Component.QrConfig;
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
@lombok.extern.slf4j.Slf4j
public class OFService extends BaseServiceImpl<OrdreFabrication, OrdreFabricationDto, OrdreFabricationDto> {

    @Autowired
    private OrdreFabricationRepository ofRepository;

    @Autowired
    private clientInventaire clientInventaire;

    @Autowired
    private com.osm.conditioning.client.clientProductionStorage productionStorageClient;

    @Autowired
    private com.osm.conditioning.projet.service.ProjetService projetService;

    public OFService(BaseRepository<OrdreFabrication> repository,
                     CodeGenerator codeGenerator,
                     QrConfig qrConfig,
                     ModelMapper modelMapper
    ) {
        super(repository, codeGenerator, qrConfig, modelMapper);
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

    @Transactional(readOnly = true)
    public List<OrdreFabricationDto> getByProject(UUID projectId) {
        return ofRepository.findAllByProjetIdAndIsDeletedFalse(projectId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrdreFabricationDto creerOF(OrdreFabricationDto dto) {
        com.osm.conditioning.projet.entity.Projet projet = null;
        if (dto.getProjectId() != null) {
            projet = projetService.findByIdOrThrow(dto.getProjectId());
        }

        // Héritage des données du projet si non spécifiées dans le DTO
        if (projet != null && dto.getProductId() == null) {
            if (projet.getProduits() != null && projet.getProduits().size() == 1) {
                // Si le projet n'a qu'un seul produit, on l'hérite automatiquement
                com.osm.conditioning.projet.entity.ProjetProduit seulProduit = projet.getProduits().get(0);
                dto.setProductId(seulProduit.getProductId());
                if (dto.getBomId() == null) {
                    dto.setBomId(seulProduit.getBomId());
                }
            } else if (projet.getProduits() != null && projet.getProduits().size() > 1) {
                // Si le projet a plusieurs produits, l'utilisateur doit en choisir un
                throw new RuntimeException("Ce projet contient plusieurs produits. Veuillez spécifier le SKU pour cet OF.");
            }
        }

        if (dto.getProductId() == null) {
            throw new RuntimeException("Le produit est obligatoire (non defini dans l'OF ni dans le projet)");
        }
        if (dto.getBomId() == null) {
            throw new RuntimeException("La BOM est obligatoire (non dÃ©finie dans l'OF ni dans le projet)");
        }

        ProductDto product = clientInventaire.getProductById(dto.getProductId());
        if (product == null) {
            throw new RuntimeException("Produit non trouve avec l'id : " + dto.getProductId());
        }

        BOMDto bom = clientInventaire.getBomById(dto.getBomId());
        if (bom == null) {
            throw new RuntimeException("BOM non trouvÃ©e avec l'id : " + dto.getBomId());
        }
        if (!bom.getProductId().equals(dto.getProductId())) {
            throw new RuntimeException("La BOM selectionnee ne correspond pas au produit");
        }
        if (dto.getLigneId() != null) {
            LigneConditionnementDto ligne = clientInventaire.getLigneById(dto.getLigneId());
            if (ligne == null) {
                throw new RuntimeException("Ligne non trouvÃ©e avec l'id : " + dto.getLigneId());
            }
        }
        BigDecimal quantiteCible = dto.getQuantiteCible();

        if (projet != null) {
            validateProjectQuantity(projet, quantiteCible, null);
        }

        // Validation de la cuve d'huile (lot vrac)
        if (dto.getLotVracId() != null) {
            try {
               ApiResponse<StorageUnitDto> resp =
                        productionStorageClient.getStorageUnit(dto.getLotVracId());
                if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                    throw new RuntimeException("Cuve d'huile introuvable (ID: " + dto.getLotVracId() + ")");
                }
            } catch (Exception e) {
                log.warn("Erreur validation cuve: {}", e.getMessage());
            }
        }

        OrdreFabrication of = new OrdreFabrication();
        of.setCode(generateCode());
        of.setProductId(dto.getProductId());
        of.setBomId(bom.getId());
        of.setLigneId(dto.getLigneId());
        of.setLotVracId(dto.getLotVracId());
        of.setQuantiteCible(quantiteCible);
        of.setDateDebutPrevue(dto.getDateDebutPrevue());
        of.setDateFinPrevue(dto.getDateFinPrevue());
        of.setStatut(StatutOF.PLANIFIE);
        of.setProjet(projet);

        for (BomLineDto lineBOMDto : bom.getLines()) {
            LigneOF ligneOF = new LigneOF();
            ligneOF.setOf(of);
            ligneOF.setArticleId(lineBOMDto.getArticleId());
            BigDecimal qteTheorique = BigDecimal.valueOf(lineBOMDto.getQuantity()).multiply(quantiteCible);
            ligneOF.setQuantiteTheorique(qteTheorique);
            of.getLignes().add(ligneOF);
        }

        OrdreFabrication saved = ofRepository.save(of);

        // GÃ©nÃ©ration du QR
        QrCodeInfo qrInfo = generateQrInfo(saved.getClass().toString(), saved.getId());
        OrdreFabricationDto result = convertToDto(saved);
        result.setPublicCode(qrInfo.getPublicCode());
        result.setQrUrl(qrInfo.getQrUrl());
        result.setQrImageBase64(qrInfo.getQrImageBase64());
        return result;
    }

    @Override
    @Transactional
    public OrdreFabricationDto update(OrdreFabricationDto dto) {
        if (dto.getId() == null) {
            throw new RuntimeException("L'ID est obligatoire pour la mise Ã  jour");
        }

        OrdreFabrication of = ofRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("OF non trouvÃ© avec l'id : " + dto.getId()));

        // Si la quantitÃ© cible ou le projet change, on valide
        BigDecimal newQuantite = dto.getQuantiteCible() != null ? dto.getQuantiteCible() : of.getQuantiteCible();
        UUID newProjectId = dto.getProjectId() != null ? dto.getProjectId() : (of.getProjet() != null ? of.getProjet().getId() : null);

        if (newProjectId != null) {
            com.osm.conditioning.projet.entity.Projet projet = projetService.findByIdOrThrow(newProjectId);
            validateProjectQuantity(projet, newQuantite, of.getId());
        }

        return super.update(dto);
    }

    private void validateProjectQuantity(com.osm.conditioning.projet.entity.Projet projet, BigDecimal quantiteCible, UUID currentOfId) {
        if (projet == null) return;

        double sumExisting = projet.getOrdresFabrication().stream()
                .filter(o -> currentOfId == null || !o.getId().equals(currentOfId))
                .mapToDouble(o -> o.getQuantiteCible().doubleValue())
                .sum();

        if (sumExisting + quantiteCible.doubleValue() > projet.getQuantiteCible()) {
            throw new RuntimeException("La quantitÃ© cumulÃ©e des OF dÃ©passe la quantitÃ© cible du projet (" + projet.getQuantiteCible() + ")");
        }
    }
    @Transactional
    public OrdreFabricationDto demarrerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        if (of.getStatut() != StatutOF.PLANIFIE && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Impossible de dÃ©marrer un OF avec le statut : " + of.getStatut());
        }

        // VÃ©rification du stock avant de dÃ©marrer
        List<String> ruptures = new ArrayList<>();
        for (LigneOF ligne : of.getLignes()) {
            UUID articleId = ligne.getArticleId();
            BigDecimal besoin = ligne.getQuantiteTheorique();

            StockSecDto stock;
            try {
                stock = clientInventaire.getStockByArticle(articleId);
            } catch (Exception e) {
                throw new RuntimeException("Impossible de rÃ©cupÃ©rer le stock pour l'article : " + articleId, e);
            }

            Integer quantiteDisponible = (stock != null && stock.getQuantiteActuelle() != null) ? stock.getQuantiteActuelle() : 0;

            if (quantiteDisponible < besoin.intValue()) {
                ruptures.add(String.format("Article %s : besoin = %d, disponible = %d",
                        articleId, besoin.intValue(), quantiteDisponible));
            }
        }

        if (!ruptures.isEmpty()) {
            throw new RuntimeException("Stock insuffisant pour dÃ©marrer l'OF : " + String.join(" ; ", ruptures));
        }

        of.setDateDebutReelle(LocalDateTime.now());
        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto mettreEnPause(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS ) {
            throw new RuntimeException("Seul un OF en cours peut Ãªtre mis en pause");
        }

        of.setStatut(StatutOF.EN_PAUSE);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto reprendreOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en pause peut Ãªtre repris");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de reprendre un OF bloquÃ© par la qualitÃ©");
        }

        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto cloturerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en cours ou en pause peut Ãªtre clÃ´turÃ©");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de clÃ´turer un OF bloquÃ©. Veuillez d'abord rÃ©soudre les problÃ¨mes qualitÃ©.");
        }
        for (LigneOF ligne : of.getLignes()) {
            BigDecimal quantiteConsommee = (ligne.getQuantiteReelle() != null) ? ligne.getQuantiteReelle() : ligne.getQuantiteTheorique();

            if (quantiteConsommee != null && quantiteConsommee.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("quantite", quantiteConsommee.intValue());
                    payload.put("motif", "Consommation OF " + of.getCode());
                    
                    if (of.getProjet() != null) {
                        clientInventaire.consommerReservation(ligne.getArticleId(), payload);
                    } else {
                        clientInventaire.sortieStock(ligne.getArticleId(), payload);
                    }
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
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("La saisie de production n'est possible que pour un OF en cours");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("La saisie de production n'est possible pour  un OF bloquÃ© par la qualitÃ©");
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
        if (of.getQuantiteBonne().compareTo(of.getQuantiteCible()) >= 0) {
            return this.cloturerOF(id);
        }

        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto ajusterConsommation(UUID id, AjustementConsommationDto ajustement) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouvÃ© avec l'id : " + id));

        LigneOF ligne = of.getLignes().stream()
                .filter(l -> l.getArticleId().equals(ajustement.getArticleId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Article non trouvÃ© dans l'OF : " + ajustement.getArticleId()));

        ligne.setQuantiteReelle(ajustement.getQuantiteReelle());
        ligne.setMotifAjustement(ajustement.getMotif());

        return convertToDto(ofRepository.save(of));
    }

    private OrdreFabricationDto convertToDto(OrdreFabrication of) {
        OrdreFabricationDto dto = modelMapper.map(of, OrdreFabricationDto.class);
        try {
            ProductDto product = clientInventaire.getProductById(of.getProductId());
            dto.setProductName(product.getName());
        } catch (Exception e) {
            dto.setProductName("N/A");
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

        if (of.getProjet() != null) {
            dto.setProjectId(of.getProjet().getId());
            dto.setProjectCode(of.getProjet().getCode());
        }

        return dto;
    }

    private String generateCode() {
        return "OF-" + System.currentTimeMillis();
    }

    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        OrdreFabrication entity = ofRepository.findByQrHex(publicCode)
                .orElseThrow(() -> new EntityNotFoundException("OF non trouvÃ© pour le code : " + publicCode));

        QrResolveResponse response = new QrResolveResponse();
        response.setEntityType("OF");
        response.setPublicCode(publicCode);
        response.setEntityId(entity.getId().toString());
        response.setLabel(entity.getCode());
        response.setStatus(entity.getStatut().name());
        response.setMobileRoute("/of/detail");
        response.setData(convertToDto(entity));
        return response;
    }
}
