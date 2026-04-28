package com.lawfirm.erp.service;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.entity.UserLoginHistory;
import com.lawfirm.erp.enums.LoginStatus;
import com.lawfirm.erp.repository.UserLoginHistoryRepository;
import com.lawfirm.erp.util.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLoginHistoryService {

    private final UserLoginHistoryRepository historyRepository;
    private final RequestContext requestContext;

    @Async
    @Transactional
    public void saveRecord(User user, LoginStatus status, String failureReason) {
        try {
            // Find existing record for this user, or create new one
            UserLoginHistory history = historyRepository.findByUser(user)
                    .orElse(UserLoginHistory.builder()
                            .user(user)
                            .loginCount(0)
                            .build());

            // Update based on status
            if (status == LoginStatus.LOGIN_SUCCESS) {
                history.setLoginTime(LocalDateTime.now());
                history.setLastLoginTime(LocalDateTime.now());
                history.setStatus(LoginStatus.LOGIN_SUCCESS);
                history.setIpAddress(requestContext.getClientIp());
                history.setDeviceInfo(requestContext.getDeviceInfo());
                history.setFailureReason(null);
                history.setLoginCount(history.getLoginCount() + 1);
                history.setLastFailedAttempt(null);
            } else {
                history.setStatus(status);
                history.setFailureReason(failureReason);
                history.setLastFailedAttempt(LocalDateTime.now());
                history.setIpAddress(requestContext.getClientIp());
                history.setDeviceInfo(requestContext.getDeviceInfo());
            }

            historyRepository.save(history);

            log.info("[LoginHistory] user={} status={} count={} ip={}",
                    user.getUsername(), status, history.getLoginCount(), requestContext.getClientIp());
        } catch (Exception e) {
            log.error("[LoginHistory] Failed to record: {}", e.getMessage());
        }
    }
}