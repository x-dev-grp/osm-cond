package com.osm.conditioning.repository;

import com.osm.conditioning.model.OfflineOperation;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfflineOperationRepository extends BaseRepository<OfflineOperation> {
    boolean existsByOperationId(String operationId);
    Optional<OfflineOperation> findByOperationId(String operationId);
}