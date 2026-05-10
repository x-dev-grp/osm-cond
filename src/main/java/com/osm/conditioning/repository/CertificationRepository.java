package com.osm.conditioning.repository;

import com.osm.conditioning.model.Certification;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificationRepository extends BaseRepository<Certification> {
    Optional<Certification> findByNameAndIsDeletedFalse(String name);

    Optional<Certification> findByCodeAndIsDeletedFalse(String code);

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByCodeAndIsDeletedFalse(String code);

    boolean existsByNameAndIdNotAndIsDeletedFalse(String name, UUID id);

    boolean existsByCodeAndIdNotAndIsDeletedFalse(String code, UUID id);
}
