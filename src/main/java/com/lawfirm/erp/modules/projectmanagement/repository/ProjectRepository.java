package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
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
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByProjectCodeAndFirmId(String projectCode, UUID firmId);

    Page<Project> findByFirmId(UUID firmId, Pageable pageable);

    Page<Project> findByFirmIdAndStatus(UUID firmId, ProjectStatus status, Pageable pageable);

    Page<Project> findByFirmIdAndClientUserId(UUID firmId, UUID clientUserId, Pageable pageable);

    /** Projects where the user is a member — for non-admin users. */
    @Query("SELECT p FROM Project p WHERE p.firmId = :firmId AND p.id IN " +
           "(SELECT pm.projectId FROM ProjectMember pm WHERE pm.userId = :userId)")
    Page<Project> findProjectsByMemberUserId(@Param("firmId") UUID firmId,
                                              @Param("userId") UUID userId,
                                              Pageable pageable);

    /** Projects visible to a specific client user (client portal). */
    List<Project> findByClientUserIdAndActive(UUID clientUserId, boolean active);

    boolean existsByProjectCodeAndFirmId(String projectCode, UUID firmId);

    long countByFirmIdAndStatus(UUID firmId, ProjectStatus status);
}
