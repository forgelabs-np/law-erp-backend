package com.lawfirm.erp.common.repository;

import com.lawfirm.erp.common.entity.UserLoginHistory;
import com.lawfirm.erp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Long> {
    Optional<UserLoginHistory> findByUser(User user);
}