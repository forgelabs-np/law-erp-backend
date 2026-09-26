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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatterRepository extends JpaRepository<Matter, UUID> {

    Page<Matter> findByFirmId(UUID firmId, Pageable pageable);

    /** "My cases" — every matter belonging to one client account. */
    Page<Matter> findByClientUserIdAndFirmId(UUID clientUserId, UUID firmId, Pageable pageable);

    /** Client-scoped matters, no filters — used by the portal timeline. */
    List<Matter> findByClientUserIdAndFirmIdOrderByCreatedAtDesc(UUID clientUserId, UUID firmId);

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
           "AND (:clientUserId IS NULL OR m.clientUserId = :clientUserId) " +
           "AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(m.matterNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Matter> findByFilters(@Param("firmId") UUID firmId,
                               @Param("matterType") MatterType matterType,
                               @Param("status") MatterStatus status,
                               @Param("clientUserId") UUID clientUserId,
                               @Param("search") String search,
                               Pageable pageable);

    /**
     * The filter set above, narrowed to a set of matter ids — how an employee's list is
     * restricted to the matters assigned to them. Callers must never pass an empty
     * collection (an empty {@code IN} is not valid JPQL); they return an empty page instead.
     */
    @Query("SELECT m FROM Matter m WHERE m.firmId = :firmId AND m.id IN :matterIds " +
           "AND (:matterType IS NULL OR m.matterType = :matterType) " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:clientUserId IS NULL OR m.clientUserId = :clientUserId) " +
           "AND (:search IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(m.matterNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Matter> findByFiltersAndIdIn(@Param("firmId") UUID firmId,
                                      @Param("matterType") MatterType matterType,
                                      @Param("status") MatterStatus status,
                                      @Param("clientUserId") UUID clientUserId,
                                      @Param("search") String search,
                                      @Param("matterIds") Collection<UUID> matterIds,
                                      Pageable pageable);

    /** Count by status — avoids loading all matters just to count. */
    @Query("SELECT COUNT(m) FROM Matter m WHERE m.firmId = :firmId AND m.status = :status")
    long countByFirmIdAndStatus(@Param("firmId") UUID firmId, @Param("status") MatterStatus status);

    /** Total count by firm — avoids loading all matters into memory. */
    @Query("SELECT COUNT(m) FROM Matter m WHERE m.firmId = :firmId")
    long countByFirmId(@Param("firmId") UUID firmId);

    /** Platform-wide count by status — no firm filter. */
    @Query("SELECT COUNT(m) FROM Matter m WHERE m.status = :status")
    long countByStatus(@Param("status") MatterStatus status);

    /** Count stale matters for a firm: matters whose leaf court case has no hearing in N days. */
    @Query("SELECT COUNT(m) FROM Matter m WHERE m.firmId = :firmId AND m.currentCourtCaseId NOT IN " +
           "(SELECT e.courtCaseId FROM CourtEvent e WHERE e.scheduledDate >= :cutoff)")
    long countStaleByFirmId(@Param("firmId") UUID firmId, @Param("cutoff") LocalDate cutoff);

    /** Count matters with a leaf court case (needed for stale calculation). */
    @Query("SELECT COUNT(m) FROM Matter m WHERE m.firmId = :firmId AND m.currentCourtCaseId IS NOT NULL")
    long countWithLeafByFirmId(@Param("firmId") UUID firmId);

    /** Get leaf court case IDs for a firm — avoids loading full Matter entities. */
    @Query("SELECT m.currentCourtCaseId FROM Matter m WHERE m.firmId = :firmId AND m.currentCourtCaseId IS NOT NULL")
    List<UUID> findLeafCourtCaseIdsByFirmId(@Param("firmId") UUID firmId);

    /** Matter IDs where the user is the assigned partner — for employee calendar filtering. */
    @Query("SELECT m.id FROM Matter m WHERE m.firmId = :firmId AND m.assignedPartnerId = :userId")
    List<UUID> findIdsByFirmIdAndAssignedPartnerId(@Param("firmId") UUID firmId,
                                                    @Param("userId") UUID userId);

    // ── Trend queries ─────────────────────────────────────────────────────

    /** Daily new matter counts grouped by date. */
    @Query("SELECT FUNCTION('DATE', m.createdAt) as d, COUNT(m) as total, " +
           "SUM(CASE WHEN m.status = 'ACTIVE' THEN 1 ELSE 0 END) as active, " +
           "SUM(CASE WHEN m.status = 'CLOSED' THEN 1 ELSE 0 END) as closed " +
           "FROM Matter m WHERE m.createdAt >= :from AND m.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', m.createdAt) ORDER BY d ASC")
    List<Object[]> countDailyByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Firm-scoped daily new matter counts. */
    @Query("SELECT FUNCTION('DATE', m.createdAt) as d, COUNT(m) as total, " +
           "SUM(CASE WHEN m.status = 'ACTIVE' THEN 1 ELSE 0 END) as active, " +
           "SUM(CASE WHEN m.status = 'CLOSED' THEN 1 ELSE 0 END) as closed " +
           "FROM Matter m WHERE m.firmId = :firmId AND m.createdAt >= :from AND m.createdAt < :to " +
           "GROUP BY FUNCTION('DATE', m.createdAt) ORDER BY d ASC")
    List<Object[]> countDailyByFirmIdAndDateRange(@Param("firmId") UUID firmId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);
}
