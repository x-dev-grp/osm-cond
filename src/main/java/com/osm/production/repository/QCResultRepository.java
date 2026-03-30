package com.osm.production.repository;

import com.osm.production.model.QCResult;
import com.xdev.xdevbase.repos.BaseRepository;
import java.util.List;
import java.util.UUID;

public interface QCResultRepository extends BaseRepository<QCResult> {
    List<QCResult> findByOfIdOrderByDateControleDesc(UUID ofId);
}