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

@Entity
@Table(name = "master_province", indexes = {
        @Index(name = "idx_mp_code", columnList = "code", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Province extends ActiveAuditableEntity {

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

    @Column(nullable = false)
    private Integer displayOrder;
}
