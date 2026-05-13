package com.osm.conditioning.model;

import com.xdev.communicator.models.enums.LabelCategory;
import com.xdev.communicator.models.enums.LabelClaimType;
import com.xdev.communicator.models.enums.LabelContentStatus;
import com.xdev.communicator.models.enums.LabelLanguage;
import com.xdev.xdevbase.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "label_content")
@Getter
@Setter
@Audited
public class LabelContent extends BaseEntity {

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "filtration_operation_id")
    private UUID filtrationOperationId;

    @Column(name = "packaging_id", nullable = false)
    private UUID packagingId;

    @Column(name = "operator_id", nullable = false)
    private UUID operatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabelContentStatus status = LabelContentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabelLanguage language = LabelLanguage.FR;

    @Column(name = "packaging_date", nullable = false)
    private LocalDate packagingDate;

    @Enumerated(EnumType.STRING)
    private LabelCategory labelCategory = LabelCategory.UNIT;

    private String legalDenomination;
    private String originCountry;
    private String netQuantity;
    private String bestBeforeDate;

    @Column(length = 1000)
    private String storageConditions;

    private String responsibleName;

    @Column(length = 1000)
    private String responsibleAddress;

    private String lotNumber;
    private String variety;
    private String qualityGrade;
    private String extractionMethod;
    private String sensoryProfile;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "label_content_certifications", joinColumns = @JoinColumn(name = "label_content_id"))
    @Column(name = "certification")
    private List<String> certifications = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "label_content_claim_types", joinColumns = @JoinColumn(name = "label_content_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type")
    private Set<LabelClaimType> claimTypes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "label_content_marketing_claims", joinColumns = @JoinColumn(name = "label_content_id"))
    @Column(name = "marketing_claim")
    private List<String> marketingClaims = new ArrayList<>();

    @Lob
    @Column(name = "final_payload_json", columnDefinition = "TEXT")
    private String finalPayloadJson;

    private LocalDateTime finalizedAt;
    private String finalizedBy;

    @OneToMany(mappedBy = "labelContent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdDate ASC")
    private List<LabelSource> sourceSnapshots = new ArrayList<>();
}
