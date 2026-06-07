package com.osm.conditioning.projet.repository;

import com.osm.conditioning.projet.entity.Client;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientRepository extends BaseRepository<Client> {

    boolean existsByCodeClient(String codeClient);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalseAndIdNot(String email, UUID id);

    boolean existsByNumeroTva(String numeroTva);

    boolean existsByNumeroTvaAndIsDeletedFalse(String numeroTva);

    boolean existsByNumeroTvaAndIsDeletedFalseAndIdNot(String numeroTva, UUID id);

    boolean existsBySiret(String siret);

    boolean existsBySiretAndIsDeletedFalse(String siret);

    boolean existsBySiretAndIsDeletedFalseAndIdNot(String siret, UUID id);

    boolean existsByTelephone(String telephone);

    boolean existsByTelephoneAndIsDeletedFalse(String telephone);

    boolean existsByTelephoneAndIsDeletedFalseAndIdNot(String telephone, UUID id);
}
