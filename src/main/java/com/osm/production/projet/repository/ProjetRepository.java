package com.osm.production.projet.repository;

import com.osm.production.projet.entity.Projet;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjetRepository extends BaseRepository<Projet> {

    Optional<Projet> findByQrHex(String qrHex);

    Optional<Projet> findByQrHexAndTenantIdAndIsDeletedFalse(String qrHex, UUID tenantId);

    Optional<Projet> findByCodeAndTenantIdAndIsDeletedFalse(String code, UUID tenantId);

    Optional<Projet> findByCodeAndIsDeletedFalse(String code);
}