package com.osm.conditioning.projet.repository;

import com.osm.conditioning.projet.entity.ProjetReservation;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjetReservationRepository extends BaseRepository<ProjetReservation> {

    @Query("""
            SELECT COUNT(pr) FROM ProjetReservation pr
            JOIN pr.projet p
            WHERE pr.articleId = :articleId
            AND COALESCE(pr.isDeleted, FALSE) = FALSE
            AND COALESCE(p.isDeleted, FALSE) = FALSE
            AND UPPER(COALESCE(pr.statut, '')) = 'CONFIRMED'
            AND UPPER(COALESCE(p.statut, '')) NOT IN ('ANNULE')
            """)
    long countConfirmedReservationsByArticleId(@Param("articleId") UUID articleId);
}
