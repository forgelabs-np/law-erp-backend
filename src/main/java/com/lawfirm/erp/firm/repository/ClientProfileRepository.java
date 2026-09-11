package com.lawfirm.erp.firm.repository;

import com.lawfirm.erp.firm.entity.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {

    Optional<ClientProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    // Count clients in a firm — used for code generation
    @Query("SELECT COUNT(cp) FROM ClientProfile cp WHERE cp.user.firm.id = :firmId")
    long countByFirmId(@Param("firmId") UUID firmId);
}