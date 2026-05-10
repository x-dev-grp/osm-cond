package com.osm.conditioning.repository;

import com.osm.conditioning.model.QCResult;
import com.xdev.xdevbase.repos.BaseRepository;
import java.util.List;
import java.util.UUID;

public interface QCResultRepository extends BaseRepository<QCResult> {
    List<QCResult> findByOfIdAndTenantIdOrderByDateControleDesc(UUID ofId, UUID tenantId);
}