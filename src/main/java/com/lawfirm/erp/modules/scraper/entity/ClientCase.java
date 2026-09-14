package com.lawfirm.erp.modules.scraper.entity;

import com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// A client case registered for tracking. ACTIVE rows drive which courts get scraped.
@Entity
@Table(name = "scraper_client_cases", indexes = {
        @Index(name = "idx_scc_court_status", columnList = "courtId, caseStatus"),
        @Index(name = "idx_scc_internal", columnList = "caseNoInternal", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No FK — decoupled from the firm module.
    private UUID clientId;

    @Column(nullable = false)
    private Integer courtId;

    @Column(length = 60)
    private String caseNoBs;

    @Column(length = 60, nullable = false)
    private String caseNoInternal;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private ClientCaseStatus caseStatus = ClientCaseStatus.ACTIVE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
