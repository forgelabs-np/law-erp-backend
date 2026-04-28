package com.lawfirm.erp.repository;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.UserLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Long> {
    Optional<UserLoginHistory> findByUser(User user);
}