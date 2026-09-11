package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.EmployeeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, UUID> {

    Optional<EmployeeProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("SELECT COUNT(ep) FROM EmployeeProfile ep WHERE ep.user.firm.id = :firmId")
    long countByFirmId(@Param("firmId") UUID firmId);
}