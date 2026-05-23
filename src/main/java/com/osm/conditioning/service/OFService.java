package com.osm.conditioning.service;

import com.osm.conditioning.Enum.QualityStatus;
import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.dto.*;
import com.osm.conditioning.model.LigneOF;
import com.osm.conditioning.model.OrdreFabrication;
import com.osm.conditioning.projet.entity.ProjetReservation;
import com.osm.conditioning.projet.repository.ProjetRepository;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import com.xdev.communicator.models.shared.ApiResponse;
import com.xdev.communicator.models.shared.StorageUnitDto;
import com.xdev.xdevbase.config.TenantContext;
import com.xdev.xdevbase.models.Action;
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
    private static final String STATUT_PROJET_EN_COURS = "EN_COURS";

    @Autowired
    private OrdreFabricationRepository ofRepository;

    @Autowired
    private clientInventaire clientInventaire;

    @Autowired
    private com.osm.conditioning.client.clientProductionStorage productionStorageClient;

    @Autowired
    private com.osm.conditioning.projet.service.ProjetService projetService;

    @Autowired
    private ProjetRepository projetRepository;

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
        ensureTraceabilityLotId(of);
        return convertToDto(of);
    }

    @Override
    public List<OrdreFabricationDto> findAll() {
        UUID tenantId = TenantContext.getCurrentTenant();
        return repository.findAllByTenantIdAndIsDeletedFalse(tenantId).stream()
                .peek(this::ensureTraceabilityLotId)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrdreFabricationDto> getByProject(UUID projectId) {
        return ofRepository.findAllByProjetIdAndIsDeletedFalse(projectId).stream()
                .peek(this::ensureTraceabilityLotId)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrdreFabricationDto creerOF(OrdreFabricationDto dto) {
        com.osm.conditioning.projet.entity.Projet projet = null;
        if (dto.getProjectId() != null) {
            projet = projetService.findByIdOrThrow(dto.getProjectId());
            projetService.ensureNotFailed(dto.getProjectId());
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
                throw new RuntimeException("Ce projet contient plusieurs produits. Veuillez specifier le SKU pour cet OF.");
            }
        }

        if (dto.getProductId() == null) {
            throw new RuntimeException("Le produit est obligatoire (non defini dans l'OF ni dans le projet)");
        }
        if (dto.getBomId() == null) {
            throw new RuntimeException("La BOM est obligatoire (non definie dans l'OF ni dans le projet)");
        }

        ProduitFinalDto product = clientInventaire.getProduitFinalById(dto.getProductId());
        if (product == null) {
            throw new RuntimeException("Produit non trouve avec l'id : " + dto.getProductId());
        }

        BOMDto bom = clientInventaire.getBomById(dto.getBomId());
        if (bom == null) {
            throw new RuntimeException("BOM non trouvee avec l'id : " + dto.getBomId());
        }
        if (!bom.getProductId().equals(dto.getProductId())) {
            throw new RuntimeException("La BOM selectionnee ne correspond pas au produit");
        }
        if (dto.getLigneId() != null) {
            LigneConditionnementDto ligne = clientInventaire.getLigneById(dto.getLigneId());
            if (ligne == null) {
                throw new RuntimeException("Ligne non trouvee avec l'id : " + dto.getLigneId());
            }
        }
        BigDecimal quantiteCible = dto.getQuantiteCible();

        if (projet != null) {
            validateProjectQuantity(projet, quantiteCible, null);
        }

        if (dto.getLotVracId() != null) {
            dto.setTraceabilityLotId(resolveTraceabilityLotId(dto.getLotVracId()));
        }

        OrdreFabrication of = new OrdreFabrication();
        of.setCode(generateCode());
        of.setProductId(dto.getProductId());
        of.setBomId(bom.getId());
        of.setLigneId(dto.getLigneId());
        of.setLotVracId(dto.getLotVracId());
        of.setTraceabilityLotId(dto.getTraceabilityLotId());
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
            throw new RuntimeException("L'ID est obligatoire pour la mise a jour");
        }

        OrdreFabrication of = ofRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("OF non trouve avec l'id : " + dto.getId()));

        BigDecimal newQuantite = dto.getQuantiteCible() != null ? dto.getQuantiteCible() : of.getQuantiteCible();
        UUID newProjectId = dto.getProjectId() != null ? dto.getProjectId() : (of.getProjet() != null ? of.getProjet().getId() : null);

        if (newProjectId != null) {
            com.osm.conditioning.projet.entity.Projet projet = projetService.findByIdOrThrow(newProjectId);
            validateProjectQuantity(projet, newQuantite, of.getId());
        }

        if (dto.getLotVracId() != null) {
            dto.setTraceabilityLotId(resolveTraceabilityLotId(dto.getLotVracId()));
        }

        return super.update(dto);
    }

    private void validateProjectQuantity(com.osm.conditioning.projet.entity.Projet projet, BigDecimal quantiteCible, UUID currentOfId) {
        if (projet == null) {
            return;
        }

        double sumExisting = projet.getOrdresFabrication().stream()
                .filter(o -> currentOfId == null || !o.getId().equals(currentOfId))
                .mapToDouble(o -> o.getQuantiteCible().doubleValue())
                .sum();

        if (sumExisting + quantiteCible.doubleValue() > projet.getQuantiteCible()) {
            throw new RuntimeException("La quantite cumulee des OF depasse la quantite cible du projet (" + projet.getQuantiteCible() + ")");
        }
    }

    @Transactional
    public OrdreFabricationDto demarrerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.PLANIFIE && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Impossible de demarrer un OF avec le statut : " + of.getStatut());
        }

        boolean projectMode = of.getProjet() != null;

        if (projectMode && of.getProjet() != null && of.getProjet().getId() != null) {
            projetService.ensureNotFailed(of.getProjet().getId());
        }

        List<String> ruptures = new ArrayList<>();
        if (!projectMode) {
            for (LigneOF ligne : of.getLignes()) {
                UUID articleId = ligne.getArticleId();
                BigDecimal besoin = ligne.getQuantiteTheorique();

                StockSecDto stock;
                try {
                    stock = getOrCreateStockForArticle(articleId);
                } catch (Exception e) {
                    throw new RuntimeException("Impossible de recuperer le stock pour l'article : " + articleId, e);
                }

                int quantiteDisponible = getStartableQuantity(stock, false);
                String stockLabel = "disponible";

                if (quantiteDisponible < besoin.intValue()) {
                    ruptures.add(String.format("Article %s : besoin = %d, %s = %d",
                            articleId, besoin.intValue(), stockLabel, quantiteDisponible));
                }
            }
        }

        if (!ruptures.isEmpty()) {
            throw new RuntimeException("Stock insuffisant pour demarrer l'OF : " + String.join(" ; ", ruptures));
        }

        of.setDateDebutReelle(LocalDateTime.now());
        of.setStatut(StatutOF.EN_COURS);

        if (of.getProjet() != null && of.getProjet().getId() != null) {
            projetService.updateStatus(of.getProjet().getId(), STATUT_PROJET_EN_COURS);
        }

        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto mettreEnPause(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("Seul un OF en cours peut etre mis en pause");
        }

        of.setStatut(StatutOF.EN_PAUSE);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto reprendreOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en pause peut etre repris");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de reprendre un OF bloque par la qualite");
        }

        of.setStatut(StatutOF.EN_COURS);
        return convertToDto(ofRepository.save(of));
    }

    @Transactional
    public OrdreFabricationDto cloturerOF(UUID id) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Seul un OF en cours ou en pause peut etre cloture");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("Impossible de cloturer un OF bloque. Veuillez d'abord resoudre les problemes qualite.");
        }

        for (LigneOF ligne : of.getLignes()) {
            BigDecimal quantiteConsommee = ligne.getQuantiteReelle() != null ? ligne.getQuantiteReelle() : ligne.getQuantiteTheorique();

            if (quantiteConsommee != null && quantiteConsommee.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("quantite", quantiteConsommee.intValue());
                    payload.put("motif", "Consommation OF " + of.getCode());

                    if (of.getProjet() != null) {
                        clientInventaire.consommerReservation(ligne.getArticleId(), payload);
                        decrementProjectReservation(of, ligne.getArticleId(), quantiteConsommee.doubleValue());
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
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS) {
            throw new RuntimeException("La saisie de production n'est possible que pour un OF en cours");
        }
        if (of.getQualityStatus() == QualityStatus.BLOCKED) {
            throw new RuntimeException("La saisie de production n'est possible que pour un OF non bloque par la qualite");
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

    private void decrementProjectReservation(OrdreFabrication of, UUID articleId, double consumedQty) {
        if (of == null || of.getProjet() == null || of.getProjet().getId() == null || consumedQty <= 0) {
            return;
        }

        com.osm.conditioning.projet.entity.Projet projet = projetRepository.findByIdAndIsDeletedFalse(of.getProjet().getId())
                .orElse(null);
        if (projet == null || projet.getReservations() == null || projet.getReservations().isEmpty()) {
            return;
        }

        for (ProjetReservation reservation : projet.getReservations()) {
            if (reservation.getArticleId() == null || !reservation.getArticleId().equals(articleId)) {
                continue;
            }
            double current = reservation.getQuantiteReservee() == null ? 0d : reservation.getQuantiteReservee();
            double updated = Math.max(0d, current - consumedQty);
            reservation.setQuantiteReservee(updated);
            if (updated == 0d && "CONFIRMED".equalsIgnoreCase(reservation.getStatut())) {
                reservation.setStatut("CONSUMED");
            }
            projetRepository.save(projet);
            return;
        }
    }

    @Transactional
    public OrdreFabricationDto ajusterConsommation(UUID id, AjustementConsommationDto ajustement) {
        OrdreFabrication of = ofRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("OF non trouve avec l'id : " + id));

        if (of.getStatut() != StatutOF.EN_COURS && of.getStatut() != StatutOF.EN_PAUSE) {
            throw new RuntimeException("Les ajustements de consommation sont autorises uniquement pour un OF en cours ou en pause");
        }
        if (ajustement.getQuantiteReelle() == null || ajustement.getQuantiteReelle().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La quantite reelle doit etre positive ou nulle");
        }

        LigneOF ligne = of.getLignes().stream()
                .filter(l -> l.getArticleId().equals(ajustement.getArticleId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Article non trouve dans l'OF : " + ajustement.getArticleId()));

        StockSecDto stock;
        try {
            stock = getOrCreateStockForArticle(ajustement.getArticleId());
        } catch (Exception e) {
            throw new RuntimeException("Impossible de recuperer le stock pour l'article : " + ajustement.getArticleId(), e);
        }

        int quantiteDemandee = ajustement.getQuantiteReelle().intValue();
        if (of.getProjet() != null) {
            int quantiteReserved = stock != null && stock.getQuantiteReservee() != null ? stock.getQuantiteReservee() : 0;
            if (quantiteDemandee > quantiteReserved) {
                throw new RuntimeException("La quantite ajustee depasse le stock reserve disponible pour cet article");
            }
        } else {
            int quantiteDisponible = stock != null && stock.getQuantiteDisponible() != null ? stock.getQuantiteDisponible() : 0;
            if (quantiteDemandee > quantiteDisponible) {
                throw new RuntimeException("La quantite ajustee depasse le stock disponible pour cet article");
            }
        }

        ligne.setQuantiteReelle(ajustement.getQuantiteReelle());
        ligne.setMotifAjustement(ajustement.getMotif());

        return convertToDto(ofRepository.save(of));
    }

    private int getStartableQuantity(StockSecDto stock, boolean projectMode) {
        if (stock == null) {
            return 0;
        }
        if (projectMode) {
            return stock.getQuantiteReservee() != null ? stock.getQuantiteReservee() : 0;
        }
        if (stock.getQuantiteDisponible() != null) {
            return stock.getQuantiteDisponible();
        }
        return stock.getQuantiteActuelle() != null ? stock.getQuantiteActuelle() : 0;
    }

    private StockSecDto getOrCreateStockForArticle(UUID articleId) {
        try {
            return clientInventaire.getStockByArticle(articleId);
        } catch (Exception firstError) {
            try {
                clientInventaire.createStockForArticle(articleId);
                return clientInventaire.getStockByArticle(articleId);
            } catch (Exception secondError) {
                throw new RuntimeException(
                        "Impossible de recuperer ou creer le stock pour l'article : " + articleId,
                        secondError
                );
            }
        }
    }

    private OrdreFabricationDto convertToDto(OrdreFabrication of) {
        OrdreFabricationDto dto = modelMapper.map(of, OrdreFabricationDto.class);
        try {
            ProduitFinalDto product = clientInventaire.getProduitFinalById(of.getProductId());
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
        dto.setTraceabilityLotId(of.getTraceabilityLotId());

        if (of.getProjet() != null) {
            dto.setProjectId(of.getProjet().getId());
            dto.setProjectCode(of.getProjet().getCode());
        }

        return dto;
    }

    private void ensureTraceabilityLotId(OrdreFabrication of) {
        if (of == null || of.getTraceabilityLotId() != null || of.getLotVracId() == null) {
            return;
        }

        UUID resolved = resolveTraceabilityLotId(of.getLotVracId());
        if (resolved != null && !resolved.equals(of.getTraceabilityLotId())) {
            of.setTraceabilityLotId(resolved);
            ofRepository.save(of);
        }
    }

    private UUID resolveTraceabilityLotId(UUID lotVracId) {
        try {
            ApiResponse<StorageUnitDto> storageResponse = productionStorageClient.getStorageUnit(lotVracId);
            if (storageResponse == null || !storageResponse.isSuccess() || storageResponse.getData() == null) {
                throw new RuntimeException("Cuve d'huile introuvable (ID: " + lotVracId + ")");
            }

            ApiResponse<com.osm.conditioning.expedition.dto.GenealogyDto> genealogyResponse =
                    productionStorageClient.getGenealogy(lotVracId);
            if (genealogyResponse != null && genealogyResponse.isSuccess() && genealogyResponse.getData() != null
                    && genealogyResponse.getData().getTraceabilityLotId() != null) {
                return genealogyResponse.getData().getTraceabilityLotId();
            }

            return lotVracId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Erreur validation lot vrac {}: {}", lotVracId, e.getMessage());
            throw new RuntimeException("Impossible de valider le lot vrac selectionne", e);
        }
    }

    private String generateCode() {
        return generateBusinessCode("code", "OF");
    }

    @Override
    public Set<Action> actionsMapping(OrdreFabrication ordreFabrication) {
        return Set.of(
                Action.READ,
                Action.CREATE,
                Action.UPDATE,
                Action.DELETE,
                Action.START,
                Action.PAUSE,
                Action.RESUME,
                Action.CLOSE,
                Action.AJUSTER_STOCK,
                Action.GEN_PDF
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QrResolveResponse resolve(String publicCode) {
        OrdreFabrication entity = ofRepository.findByQrHex(publicCode)
                .orElseThrow(() -> new EntityNotFoundException("OF non trouve pour le code : " + publicCode));

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
