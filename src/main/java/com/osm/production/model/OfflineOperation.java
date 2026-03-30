package com.osm.production.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_offline_operation")
@Getter
@Setter
public class OfflineOperation extends BaseEntity implements Serializable {

    @Column(nullable = false)
    private String operationId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String method;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode responseBody;

    @Column(nullable = false)
    private String status;

    private String errorMessage;

    private LocalDateTime syncedAt;
}