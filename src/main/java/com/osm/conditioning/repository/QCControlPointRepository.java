package com.osm.conditioning.repository;


import com.osm.conditioning.model.QCControlPoint;
import com.xdev.xdevbase.repos.BaseRepository;

import java.util.List;
import java.util.UUID;

public interface QCControlPointRepository extends BaseRepository<QCControlPoint> {
        List<QCControlPoint> findByPlanIdAndBlockingTrue(UUID planId);
        List<QCControlPoint> findByPlanId(UUID planId);
    }
