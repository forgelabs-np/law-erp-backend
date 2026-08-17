package com.lawfirm.erp.modules.scraper.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// courtId matches the site's URL path segment (39 = Kathmandu) and is the natural id.
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
