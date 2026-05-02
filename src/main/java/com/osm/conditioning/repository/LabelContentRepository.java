package com.osm.conditioning.repository;

import com.osm.conditioning.model.LabelContent;
import com.xdev.xdevbase.repos.BaseRepository;

import java.util.List;
import java.util.UUID;

public interface LabelContentRepository extends BaseRepository<LabelContent> {
    List<LabelContent> findAllByLotIdAndIsDeletedFalse(UUID lotId);
}
