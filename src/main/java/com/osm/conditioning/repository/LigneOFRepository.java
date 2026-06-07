package com.osm.conditioning.repository;

import com.osm.conditioning.Enum.StatutOF;
import com.osm.conditioning.model.LigneOF;
import com.xdev.xdevbase.repos.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface LigneOFRepository extends BaseRepository<LigneOF> {

    @Query("""
            SELECT COUNT(l) FROM LigneOF l
            JOIN l.of o
            WHERE l.articleId = :articleId
            AND COALESCE(l.isDeleted, FALSE) = FALSE
            AND COALESCE(o.isDeleted, FALSE) = FALSE
            AND o.statut IN :activeStatuses
            """)
    long countByArticleIdAndActiveOfStatuses(
            @Param("articleId") UUID articleId,
            @Param("activeStatuses") Collection<StatutOF> activeStatuses
    );
}
