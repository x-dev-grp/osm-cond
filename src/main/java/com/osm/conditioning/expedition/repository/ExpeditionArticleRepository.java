package com.osm.conditioning.expedition.repository;

import com.osm.conditioning.expedition.model.ExpeditionArticle;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpeditionArticleRepository extends BaseRepository<ExpeditionArticle> {
    Optional<ExpeditionArticle> findByIdAndExpeditionIdAndIsDeletedFalse(UUID id, UUID expeditionId);
}
