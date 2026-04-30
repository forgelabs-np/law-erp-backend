package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    List<Feature> findByModuleId(Long moduleId);
}