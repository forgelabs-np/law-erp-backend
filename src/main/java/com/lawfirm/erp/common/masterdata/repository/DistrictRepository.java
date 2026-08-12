package com.lawfirm.erp.common.masterdata.repository;

import com.lawfirm.erp.common.masterdata.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DistrictRepository extends JpaRepository<District, UUID> {

    Optional<District> findByCode(String code);

    List<District> findByProvinceIdOrderByNameEnAsc(UUID provinceId);

    List<District> findAllByOrderByNameEnAsc();
}
