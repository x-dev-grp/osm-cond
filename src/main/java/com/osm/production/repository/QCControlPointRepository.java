package com.osm.production.repository;


import com.osm.production.model.QCControlPoint;
import com.xdev.xdevbase.repos.BaseRepository;

import java.util.List;
import java.util.UUID;

public interface QCControlPointRepository extends BaseRepository<QCControlPoint> {
    // Dans QCControlPointRepository.java
        List<QCControlPoint> findByPlanIdAndBlockingTrue(UUID planId);
        List<QCControlPoint> findByPlanId(UUID planId);
    }
