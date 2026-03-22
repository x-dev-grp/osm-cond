package com.osm.production.client;


import com.osm.production.dto.ArticleSecDto;
import com.osm.production.dto.BOMDto;
import com.osm.production.dto.LigneConditionnementDto;
import com.osm.production.dto.SKUDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface clientInventaire {

    @GetMapping("/api/inventaire/skus/{id}")
    SKUDto getSkuById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/articles/{id}")
    ArticleSecDto getArticleById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/lignes/{id}")
    LigneConditionnementDto getLigneById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/boms/{id}")
    BOMDto getBomById(@PathVariable("id") UUID id);

}