package com.osm.conditioning.expedition.repository;

import com.osm.conditioning.expedition.model.Expedition;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpeditionRepository extends BaseRepository<Expedition> {
    Optional<Expedition> findByIdAndIsDeletedFalse(UUID id);

    Optional<Expedition> findByQrHexAndIsDeletedFalse(String qrHex);

    Optional<Expedition> findByExpeditionNumberIgnoreCaseAndIsDeletedFalse(String expeditionNumber);

    List<Expedition> findAllByIsDeletedFalseOrderByCreatedDateDesc();

    List<Expedition> findAllByProjetIdAndIsDeletedFalseOrderByCreatedDateDesc(UUID projetId);
}
