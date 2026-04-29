package com.osm.conditioning.repository;

import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.model.OrdreFabrication;
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
    List<OrdreFabrication> findAllByProjetIdAndIsDeletedFalse(UUID projetId);
}