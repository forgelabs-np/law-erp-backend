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

import java.util.UUID;

@Entity
@Table(name = "master_district", indexes = {
        @Index(name = "idx_md_code", columnList = "code", unique = true),
        @Index(name = "idx_md_province", columnList = "provinceId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class District extends ActiveAuditableEntity {

    @Column(length = 20, nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private UUID provinceId;

    @Column(length = 8, nullable = false)
    private String provinceCode;

    @Column(length = 100, nullable = false)
    private String nameEn;

    @Column(length = 100)
    private String nameNp;

    @Column(length = 100)
    private String headquarters;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal areaKm2;

    private Long population2021;
}
