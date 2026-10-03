package com.lawfirm.erp.modules.document.repository;

import com.lawfirm.erp.modules.document.entity.Document;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Row-level scope lives in these queries.
 *
 * <p>Three variants rather than one parameterised query: the caller's scope decides which
 * single query runs, so no request can widen its own result set by supplying different
 * arguments. Scope is expressed as subqueries, which also means an unassigned employee gets
 * an empty page naturally instead of needing an empty-{@code IN} guard.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndFirmId(Long id, UUID firmId);

    /** Firm admin / Super Admin: every document in the firm. */
    @Query("SELECT d FROM Document d WHERE d.firmId = :firmId "
           + "AND (:matterId IS NULL OR d.matterId = :matterId) "
           + "AND (:projectId IS NULL OR d.projectId = :projectId) "
           + "AND (:status IS NULL OR d.status = :status) "
           + "AND (:visibility IS NULL OR d.visibility = :visibility) "
           + "AND (:search IS NULL OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Document> findForFirm(@Param("firmId") UUID firmId,
                              @Param("matterId") UUID matterId,
                              @Param("projectId") UUID projectId,
                              @Param("status") DocumentStatus status,
                              @Param("visibility") DocumentVisibility visibility,
                              @Param("search") String search,
                              Pageable pageable);

    /** Firm staff: only the cases they are assigned to and the projects they are a member of. */
    @Query("SELECT d FROM Document d WHERE d.firmId = :firmId "
           + "AND (d.matterId IN (SELECT ca.matterId FROM CaseAssignment ca WHERE ca.userId = :userId) "
           + "  OR d.projectId IN (SELECT pm.projectId FROM ProjectMember pm WHERE pm.userId = :userId)) "
           + "AND (:matterId IS NULL OR d.matterId = :matterId) "
           + "AND (:projectId IS NULL OR d.projectId = :projectId) "
           + "AND (:status IS NULL OR d.status = :status) "
           + "AND (:visibility IS NULL OR d.visibility = :visibility) "
           + "AND (:search IS NULL OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Document> findAssignedTo(@Param("firmId") UUID firmId,
                                 @Param("userId") UUID userId,
                                 @Param("matterId") UUID matterId,
                                 @Param("projectId") UUID projectId,
                                 @Param("status") DocumentStatus status,
                                 @Param("visibility") DocumentVisibility visibility,
                                 @Param("search") String search,
                                 Pageable pageable);

    /**
     * Client portal: their own cases and projects, shared documents only.
     *
     * <p>The case link is resolved from {@code Matter.clientUserId} <em>or</em> a party row
     * marked as our client, so matters created before that column existed still surface.
     */
    @Query("SELECT d FROM Document d WHERE d.firmId = :firmId AND d.visibility = :shared AND d.status = :status "
           + "AND (d.matterId IN (SELECT m.id FROM Matter m WHERE m.clientUserId = :userId) "
           + "  OR d.matterId IN (SELECT mp.matterId FROM MatterParty mp WHERE mp.clientId = :userId AND mp.isOurClient = true) "
           + "  OR d.projectId IN (SELECT p.id FROM Project p WHERE p.clientUserId = :userId)) "
           + "AND (:matterId IS NULL OR d.matterId = :matterId) "
           + "AND (:projectId IS NULL OR d.projectId = :projectId) "
           + "AND (:search IS NULL OR LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Document> findVisibleToClient(@Param("firmId") UUID firmId,
                                      @Param("userId") UUID userId,
                                      @Param("shared") DocumentVisibility shared,
                                      @Param("status") DocumentStatus status,
                                      @Param("matterId") UUID matterId,
                                      @Param("projectId") UUID projectId,
                                      @Param("search") String search,
                                      Pageable pageable);
}
