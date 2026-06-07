package com.osm.conditioning.repository;


import com.osm.conditioning.model.QCControlPoint;
import com.xdev.xdevbase.repos.BaseRepository;

import java.util.List;
import java.util.UUID;

public interface QCControlPointRepository extends BaseRepository<QCControlPoint> {
    List<QCControlPoint> findByPlanIdAndBlockingTrueAndIsDeletedFalse(UUID planId);

    List<QCControlPoint> findByPlanIdAndIsDeletedFalse(UUID planId);
}
