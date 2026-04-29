package com.osm.conditioning.shipping.repository;

import com.osm.conditioning.shipping.model.ShippingLine;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingLineRepository extends BaseRepository<ShippingLine> {
    Optional<ShippingLine> findByIdAndShippingInfoIdAndIsDeletedFalse(UUID id, UUID shippingInfoId);
}

