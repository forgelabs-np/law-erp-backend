package com.lawfirm.erp.modules.projectmanagement.repository;

import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RenewalRepository extends JpaRepository<Renewal, Long> {

    List<Renewal> findByProjectIdAndActive(UUID projectId, boolean active);

    List<Renewal> findByAssignedToIdAndActive(UUID assignedToId, boolean active);

    long countByProjectIdAndActive(UUID projectId, boolean active);

    long countByProjectIdAndStatus(UUID projectId, RenewalStatus status);
}
