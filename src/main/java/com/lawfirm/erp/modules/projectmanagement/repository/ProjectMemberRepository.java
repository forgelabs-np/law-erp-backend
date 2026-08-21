package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.ProjectMember;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(UUID projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserIdAndRoleInProjectIn(UUID projectId, UUID userId, List<ProjectMemberRole> roles);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);

    @Query("SELECT pm.userId FROM ProjectMember pm WHERE pm.projectId = :projectId")
    List<UUID> findUserIdsByProjectId(@Param("projectId") UUID projectId);
}
