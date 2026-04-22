package com.osm.production.repository;

import com.osm.production.Enum.StatutOF;
import com.osm.production.model.OrdreFabrication;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdreFabricationRepository extends BaseRepository<OrdreFabrication> {
    Optional<OrdreFabrication> findByCodeAndTenantIdAndIsDeletedFalse(String code, UUID tenantId);
    List<OrdreFabrication> findByStatut(StatutOF statut);
    List<OrdreFabrication> findByDateDebutPrevueBetween(LocalDateTime debut, LocalDateTime fin);
    // Pas de findByPublicCode / existsByPublicCode ici
}