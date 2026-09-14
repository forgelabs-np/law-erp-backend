package com.lawfirm.erp.modules.scraper.repository;

import com.lawfirm.erp.modules.scraper.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourtRepository extends JpaRepository<Court, Integer> {

    Optional<Court> findByCourtId(Integer courtId);

    List<Court> findByIsActiveTrueOrderByCourtIdAsc();

    List<Court> findByCourtTypeOrderByCourtNameEnglishAsc(String courtType);

    @Query("SELECT DISTINCT c.courtId FROM Court c WHERE c.isActive = true")
    List<Integer> findActiveCourtIds();
}
