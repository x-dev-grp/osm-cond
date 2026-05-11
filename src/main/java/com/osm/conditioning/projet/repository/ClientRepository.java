package com.osm.conditioning.projet.repository;



import com.osm.conditioning.projet.entity.Client;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends BaseRepository<Client> {

    boolean existsByCodeClient(String codeClient);

    boolean existsByEmail(String email);
    boolean existsByNumeroTva(String numeroTva);
    boolean existsBySiret(String siret);
    boolean existsByTelephone(String telephone);
}