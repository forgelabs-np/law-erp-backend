package com.lawfirm.erp.modules.scraper.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Court registry. courtId matches the site's URL path segment
 * (e.g. 39 = Kathmandu District, 63 = Gulmi District) and is the single natural id.
 * Standalone entity (not auditable) because courtId — not a generated UUID — is the key.
 */
@Entity
@Table(name = "scraper_courts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Court {

    @Id
    @Column(name = "court_id", nullable = false)
    private Integer courtId;

    @Column(length = 200)
    private String courtName;

    @Column(length = 30)
    private String courtType;

    /** Manual override to stop scraping a court even if client cases reference it. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
