package com.lawfirm.erp.modules.scraper.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// courtId matches the site's URL path segment (39 = Kathmandu) and is the natural id.
// Nepali name is the site's exact link text; English name is our translation for display.
@Entity
@Table(name = "scraper_courts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Court {

    @Id
    @Column(name = "court_id", nullable = false)
    private Integer courtId;

    @Column(name = "court_name_nepali", length = 200)
    private String courtNameNepali;

    @Column(name = "court_name_english", length = 200)
    private String courtNameEnglish;

    @Column(length = 30)
    private String courtType;

    /** Manual override to stop scraping a court even if client cases reference it. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
