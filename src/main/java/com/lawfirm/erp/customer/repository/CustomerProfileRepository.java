package com.lawfirm.erp.customer.repository;

import com.lawfirm.erp.customer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByUserId(UUID userId);

    Optional<CustomerProfile> findByNationalId(String nationalId);

    @Query("SELECT c FROM CustomerProfile c JOIN c.user u WHERE c.firm.id = :firmId " +
           "AND (:fullName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:fullName AS string), '%'))) " +
           "AND (:mobileNo IS NULL OR u.mobileNo = :mobileNo) " +
           "AND (:email IS NULL OR u.email = :email)")
    List<CustomerProfile> findMatches(@Param("firmId") UUID firmId,
                                      @Param("fullName") String fullName,
                                      @Param("mobileNo") String mobileNo,
                                      @Param("email") String email);
}