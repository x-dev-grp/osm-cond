package com.osm.conditioning.service;

import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.dto.InventoryUsageBlockersDto;
import com.osm.conditioning.projet.repository.ProjetProduitRepository;
import com.osm.conditioning.projet.repository.ProjetReservationRepository;
import com.osm.conditioning.repository.LigneOFRepository;
import com.osm.conditioning.repository.OrdreFabricationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
public class InventoryUsageService {

    private static final EnumSet<StatutOF> ACTIVE_OF_STATUSES = EnumSet.of(
            StatutOF.PLANIFIE,
            StatutOF.EN_COURS,
            StatutOF.EN_PAUSE
    );

    private final ProjetReservationRepository projetReservationRepository;
    private final LigneOFRepository ligneOFRepository;
    private final ProjetProduitRepository projetProduitRepository;
    private final OrdreFabricationRepository ordreFabricationRepository;

    public InventoryUsageService(ProjetReservationRepository projetReservationRepository,
                                 LigneOFRepository ligneOFRepository,
                                 ProjetProduitRepository projetProduitRepository,
                                 OrdreFabricationRepository ordreFabricationRepository) {
        this.projetReservationRepository = projetReservationRepository;
        this.ligneOFRepository = ligneOFRepository;
        this.projetProduitRepository = projetProduitRepository;
        this.ordreFabricationRepository = ordreFabricationRepository;
    }

    @Transactional(readOnly = true)
    public InventoryUsageBlockersDto getArticleUsageBlockers(UUID articleId) {
        InventoryUsageBlockersDto result = new InventoryUsageBlockersDto();

        long reservationCount = projetReservationRepository.countConfirmedReservationsByArticleId(articleId);
        if (reservationCount > 0) {
            result.getBlockers().add(new InventoryUsageBlockersDto.Blocker(
                    "ARTICLE_USED_IN_PROJET_RESERVATION",
                    "L'article est reserve par un ou plusieurs projets actifs",
                    reservationCount
            ));
        }

        long ofLineCount = ligneOFRepository.countByArticleIdAndActiveOfStatuses(articleId, ACTIVE_OF_STATUSES);
        if (ofLineCount > 0) {
            result.getBlockers().add(new InventoryUsageBlockersDto.Blocker(
                    "ARTICLE_USED_IN_OF",
                    "L'article est utilise par un ou plusieurs ordres de fabrication en cours",
                    ofLineCount
            ));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public InventoryUsageBlockersDto getProductUsageBlockers(UUID productId) {
        InventoryUsageBlockersDto result = new InventoryUsageBlockersDto();

        long projetCount = projetProduitRepository.countActiveProjectsByProductId(productId);
        if (projetCount > 0) {
            result.getBlockers().add(new InventoryUsageBlockersDto.Blocker(
                    "PRODUIT_USED_IN_PROJET",
                    "Le produit fini est reference par un ou plusieurs projets actifs",
                    projetCount
            ));
        }

        long ofCount = ordreFabricationRepository.countByProductIdAndStatutInAndIsDeletedFalse(
                productId,
                ACTIVE_OF_STATUSES
        );
        if (ofCount > 0) {
            result.getBlockers().add(new InventoryUsageBlockersDto.Blocker(
                    "PRODUIT_USED_IN_OF",
                    "Le produit fini est utilise par un ou plusieurs ordres de fabrication en cours",
                    ofCount
            ));
        }

        return result;
    }
}
