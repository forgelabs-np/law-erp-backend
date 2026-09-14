package com.lawfirm.erp.common.masterdata.repository;

import com.lawfirm.erp.common.masterdata.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    Optional<Country> findByCode(String code);
}
