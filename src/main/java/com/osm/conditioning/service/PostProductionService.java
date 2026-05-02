package com.osm.conditioning.service;


import com.osm.conditioning.client.clientInventaire;
import com.osm.conditioning.client.clientProductionDelivery;
import com.osm.conditioning.dto.ArticleSecDto;
import com.osm.conditioning.model.OrdreFabrication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostProductionService {

    private final clientProductionDelivery deliveryClient;
    private final clientInventaire inventaireClient;

    public void creerArticlesApresOF(OrdreFabrication of, List<ArticleSecDto> articles) {
        // ... (code fourni précédemment)
    }
}