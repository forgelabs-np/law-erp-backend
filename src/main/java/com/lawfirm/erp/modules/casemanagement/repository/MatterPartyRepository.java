package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatterPartyRepository extends JpaRepository<MatterParty, UUID> {

    List<MatterParty> findByMatterIdAndFirmId(UUID matterId, UUID firmId);

    /** Our-client parties for a batch of matters — hearing-reminder recipient resolution. */
    @Query("SELECT p FROM MatterParty p WHERE p.matterId IN :matterIds AND p.firmId = :firmId AND p.isOurClient = true")
    List<MatterParty> findByMatterIdInAndFirmIdAndOurClientTrue(@Param("matterIds") List<UUID> matterIds,
                                                                @Param("firmId") UUID firmId);

    Optional<MatterParty> findByIdAndFirmId(UUID id, UUID firmId);

    @Query("SELECT p FROM MatterParty p WHERE p.firmId = :firmId " +
           "AND (:name IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:mobileNo IS NULL OR p.mobileNo = :mobileNo) " +
           "AND (:email IS NULL OR LOWER(p.email) = LOWER(CAST(:email AS string)))")
    List<MatterParty> findMatches(@Param("firmId") UUID firmId,
                                  @Param("name") String name,
                                  @Param("mobileNo") String mobileNo,
                                  @Param("email") String email);
}
