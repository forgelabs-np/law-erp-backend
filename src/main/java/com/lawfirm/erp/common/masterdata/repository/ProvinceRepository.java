package com.lawfirm.erp.common.masterdata.repository;

import com.lawfirm.erp.common.masterdata.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvinceRepository extends JpaRepository<Province, UUID> {

    Optional<Province> findByCode(String code);

    List<Province> findAllByOrderByDisplayOrderAscIdAsc();
}
