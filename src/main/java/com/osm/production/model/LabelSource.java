package com.osm.production.model;

import com.xdev.communicator.models.enums.LabelSourceType;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

//La photo de ce que j'ai utilisé pour écrire l'étiquette
@Entity
@Table(name = "label_source_snapshot")
@Getter
@Setter
public class LabelSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_content_id", nullable = false)
    private LabelContent labelContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabelSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    private String sourceBusinessKey;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;
}
