package com.lawfirm.erp.common.masterdata.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master data — a province of Nepal (first-level administrative division).
 * Seeded from {@code classpath:master-data/nepal/provinces.json}; read-mostly,
 * served through the {@code masterData} cache.
 */
@Entity
@Table(name = "master_province", indexes = {
        @Index(name = "idx_mp_code", columnList = "code", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Province extends ActiveAuditableEntity {

    /** Official ISO 3166-2 code, e.g. NP-P1. */
    @Column(length = 8, nullable = false, unique = true)
    private String code;

    @Column(length = 100, nullable = false)
    private String nameEn;

    @Column(length = 100)
    private String nameNp;

    @Column(length = 100)
    private String capitalEn;

    @Column(length = 100)
    private String capitalNp;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal areaKm2;

    private Long population2021;

    /** Seed order (1..7) — used for stable listing. */
    @Column(nullable = false)
    private Integer displayOrder;
}
