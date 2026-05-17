package com.osm.conditioning.repository;

import com.osm.conditioning.model.LabelContent;
import com.xdev.xdevbase.repos.BaseRepository;

import java.util.List;
import java.util.UUID;

public interface LabelContentRepository extends BaseRepository<LabelContent> {
    java.util.Optional<LabelContent> findByIdAndIsDeletedFalse(UUID id);
    List<LabelContent> findAllByLotIdAndIsDeletedFalse(UUID lotId);
    List<LabelContent> findAllByPackagingIdAndIsDeletedFalse(UUID packagingId);
    List<LabelContent> findAllByProductIdAndIsDeletedFalse(UUID productId);
}
