package com.osm.production.repository;


import com.osm.production.model.QCPlan;
import com.xdev.xdevbase.repos.BaseRepository;
import java.util.Optional;
import java.util.UUID;

public interface QCPlanRepository extends BaseRepository<QCPlan> {
    Optional<QCPlan> findByOfIdAndActifTrue(UUID ofId);
}