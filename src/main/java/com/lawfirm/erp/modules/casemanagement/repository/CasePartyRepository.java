package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.CaseParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CasePartyRepository extends JpaRepository<CaseParty, UUID> {

    List<CaseParty> findByCaseId(UUID caseId);

    List<CaseParty> findByCaseIdAndFirmId(UUID caseId, UUID firmId);

    @Query("SELECT cp FROM CaseParty cp WHERE cp.firmId = :firmId " +
           "AND (:fullName IS NULL OR LOWER(cp.fullName) LIKE LOWER(CONCAT('%', CAST(:fullName AS string), '%'))) " +
           "AND (:mobileNo IS NULL OR cp.mobileNo = :mobileNo) " +
           "AND (:email IS NULL OR cp.email = :email)")
    List<CaseParty> findMatches(@Param("firmId") UUID firmId,
                                @Param("fullName") String fullName,
                                @Param("mobileNo") String mobileNo,
                                @Param("email") String email);

    void deleteByCaseIdAndFirmId(UUID caseId, UUID firmId);

    boolean existsByCaseIdAndFirmId(UUID caseId, UUID firmId);
}
