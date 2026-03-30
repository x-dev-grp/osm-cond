package com.osm.production.repository;

import com.osm.production.model.OrdreFabrication;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdreFabricationRepository extends BaseRepository<OrdreFabrication> {

    // Pas de findByPublicCode / existsByPublicCode ici
}