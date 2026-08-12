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
 * Master data — the country (first level of the reference hierarchy: country → province → district).
 *
 * Currently a single row (Nepal); kept as a table so future multi-country tenancies can extend it
 * without schema changes. Seeded from {@code MasterDataSeeder}.
 */
@Entity
@Table(name = "master_country", indexes = {
        @Index(name = "idx_mc_code", columnList = "code", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Country extends ActiveAuditableEntity {

    /** Official ISO 3166-1 alpha-2 code, e.g. NP. */
    @Column(length = 2, nullable = false, unique = true)
    private String code;

    /** Official ISO 3166-1 alpha-3 code, e.g. NPL. */
    @Column(length = 3, nullable = false)
    private String iso3;

    /** Title in English, e.g. "Nepal". */
    @Column(length = 100, nullable = false)
    private String nameEn;

    /** Title in Nepali (Devanagari), e.g. "नेपाल". */
    @Column(length = 100)
    private String nameNp;
}
