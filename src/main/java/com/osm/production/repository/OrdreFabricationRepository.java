package com.osm.production.repository;


import com.osm.production.Enum.StatutOF;
import com.osm.production.model.OrdreFabrication;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrdreFabricationRepository extends BaseRepository<OrdreFabrication> {
    List<OrdreFabrication> findByStatut(StatutOF statut);
    List<OrdreFabrication> findByDateDebutPrevueBetween(LocalDateTime debut, LocalDateTime fin);
}