package com.lawfirm.erp.modules.casemanagement.repository;

import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatterRepository extends JpaRepository<Matter, UUID> {

    Page<Matter> findByFirmId(UUID firmId, Pageable pageable);

    Optional<Matter> findByMatterNumberAndFirmId(String matterNumber, UUID firmId);

    Optional<Matter> findByIdAndFirmId(UUID id, UUID firmId);

    @Query("SELECT m.matterNumber FROM Matter m WHERE m.firmId = :firmId " +
           "AND m.matterNumber LIKE :pattern ORDER BY m.matterNumber DESC")
    List<String> findMaxMatterNumberByPattern(@Param("firmId") UUID firmId,
                                              @Param("pattern") String pattern,
                                              Pageable pageable);

    @Query("SELECT m FROM Matter m WHERE m.firmId = :firmId " +
           "AND (:matterType IS NULL OR m.matterType = :matterType) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(m.matterNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Matter> findByFilters(@Param("firmId") UUID firmId,
                               @Param("matterType") MatterType matterType,
                               @Param("status") MatterStatus status,
                               @Param("search") String search,
                               Pageable pageable);
}
