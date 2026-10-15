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
@Table(name = "master_country", indexes = {
        @Index(name = "idx_mc_code", columnList = "code", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Country extends ActiveAuditableEntity {

    @Column(length = 2, nullable = false, unique = true)
    private String code;

    @Column(length = 3, nullable = false)
    private String iso3;

    @Column(length = 100, nullable = false)
    private String nameEn;

    @Column(length = 100)
    private String nameNp;
}
