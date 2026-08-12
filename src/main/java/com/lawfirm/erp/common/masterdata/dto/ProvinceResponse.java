package com.lawfirm.erp.common.masterdata.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * Serializable so cached entries stay safe if the cache ever moves off-heap.
 */
@Data
@Builder
public class ProvinceResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;

    private String code;

    private String nameEn;

    private String nameNp;

    private String capitalEn;

    private String capitalNp;

    private java.math.BigDecimal areaKm2;

    private Long population2021;

    private Integer districtCount;
}
