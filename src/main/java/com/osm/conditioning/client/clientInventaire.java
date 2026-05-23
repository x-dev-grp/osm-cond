package com.osm.conditioning.client;

import com.osm.conditioning.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}", configuration = com.xdev.xdevsecurity.config.FeignConfiguration.class)
public interface clientInventaire {

    @GetMapping("/api/inventaire/products/{id}")
    ProduitFinalDto getProduitFinalById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/articles/{id}")
    ArticleSecDto getArticleById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/lignes/{id}")
    LigneConditionnementDto getLigneById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/boms/{id}")
    BOMDto getBomById(@PathVariable("id") UUID id);

    @GetMapping("/api/inventaire/boms/product/{productId}/active")
    BOMDto getActiveBomForProduct(@PathVariable("productId") UUID productId);

    @GetMapping("/api/inventaire/stocks/article/{articleId}")
    StockSecDto getStockByArticle(@PathVariable("articleId") UUID articleId);

    @PostMapping("/api/inventaire/stocks/article/{articleId}")
    StockSecDto createStockForArticle(@PathVariable("articleId") UUID articleId);

    @PutMapping("/api/inventaire/stocks/{articleId}/sortie")
    StockSecDto sortieStock(@PathVariable("articleId") UUID articleId,
                            @RequestBody Map<String, Object> payload);

    @PutMapping("/api/inventaire/stocks/{articleId}/reserver")
    StockSecDto reserverStock(@PathVariable("articleId") UUID articleId,
                              @RequestBody Map<String, Object> payload);

    @PutMapping("/api/inventaire/stocks/{articleId}/annuler-reservation")
    StockSecDto annulerReservation(@PathVariable("articleId") UUID articleId,
                                   @RequestBody Map<String, Object> payload);

    @PutMapping("/api/inventaire/stocks/{articleId}/consommer-reservation")
    StockSecDto consommerReservation(@PathVariable("articleId") UUID articleId,
                                      @RequestBody Map<String, Object> payload);
}
