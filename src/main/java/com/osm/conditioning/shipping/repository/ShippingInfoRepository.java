package com.osm.conditioning.shipping.repository;

import com.osm.conditioning.shipping.model.ShippingInfo;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingInfoRepository extends BaseRepository<ShippingInfo> {
    Optional<ShippingInfo> findByProjetIdAndIsDeletedFalse(UUID projetId);

    Optional<ShippingInfo> findByIdAndIsDeletedFalse(UUID id);

    Optional<ShippingInfo> findByQrHexAndIsDeletedFalse(String qrHex);

    Optional<ShippingInfo> findByShippingNumberIgnoreCaseAndIsDeletedFalse(String shippingNumber);

    List<ShippingInfo> findAllByIsDeletedFalseOrderByCreatedDateDesc();

    List<ShippingInfo> findAllByTenantIdAndIsDeletedFalseOrderByCreatedDateDesc(UUID tenantId);
}
