package com.osm.conditioning.projet.service;

import com.osm.conditioning.projet.entity.Projet;
import com.osm.conditioning.projet.repository.ProjetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchDebugger implements CommandLineRunner {
    private final ProjetRepository projetRepository;

    public SearchDebugger(ProjetRepository projetRepository) {
        this.projetRepository = projetRepository;
    }

    @Override
    @jakarta.transaction.Transactional
    public void run(String... args) throws Exception {
        System.out.println("--- DEBUG SEARCH ---");
        Iterable<Projet> projets = projetRepository.findAll();
        for (Projet p : projets) {
            System.out.println("Projet: ID=" + p.getId() + ", Code=" + p.getCode() + ", QrHex=" + p.getQrHex() + ", Tenant=" + p.getTenantId());
        }
        System.out.println("--- END DEBUG ---");
    }
}
