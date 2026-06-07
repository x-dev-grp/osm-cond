package com.osm.conditioning.repository;


import com.osm.conditioning.model.QCPlan;
import com.xdev.xdevbase.repos.BaseRepository;
import java.util.Optional;
import java.util.UUID;

public interface QCPlanRepository extends BaseRepository<QCPlan> {
    Optional<QCPlan> findByOfIdAndActifTrueAndIsDeletedFalse(UUID ofId);
}