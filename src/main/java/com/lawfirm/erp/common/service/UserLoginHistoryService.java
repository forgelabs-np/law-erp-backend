package com.lawfirm.erp.common.service;

import com.lawfirm.erp.common.entity.UserLoginHistory;
import com.lawfirm.erp.common.enums.LoginStatus;
import com.lawfirm.erp.common.repository.UserLoginHistoryRepository;
import com.lawfirm.erp.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLoginHistoryService {

    private final UserLoginHistoryRepository historyRepository;

    /**
     * Records login history.
     *
     * FIX: RequestContext used to call RequestContextHolder inside @Async thread,
     * which has no bound request — throws IllegalStateException.
     *
     * Solution: capture IP and deviceInfo SYNCHRONOUSLY on the calling thread
     * (where the request IS available), then pass the plain strings to the
     * async write method. Same pattern we used for AuditService.
     */
    public void saveRecord(User user, LoginStatus status, String failureReason) {
        // Capture request data NOW — on the main request thread
        String ip         = resolveClientIp();
        String deviceInfo = resolveDeviceInfo();

        // Write asynchronously with plain values — no thread-local reads needed
        saveAsync(user, status, failureReason, ip, deviceInfo);
    }

    @Async
    @Transactional
    protected void saveAsync(User user, LoginStatus status, String failureReason,
                             String ip, String deviceInfo) {
        try {
            UserLoginHistory history = historyRepository.findByUser(user)
                    .orElse(UserLoginHistory.builder()
                            .user(user)
                            .loginCount(0)
                            .build());

            if (status == LoginStatus.LOGIN_SUCCESS) {
                history.setLoginTime(LocalDateTime.now());
                history.setLastLoginTime(LocalDateTime.now());
                history.setStatus(LoginStatus.LOGIN_SUCCESS);
                history.setIpAddress(ip);
                history.setDeviceInfo(deviceInfo);
                history.setFailureReason(null);
                history.setLoginCount(history.getLoginCount() + 1);
                history.setLastFailedAttempt(null);
            } else {
                history.setStatus(status);
                history.setFailureReason(failureReason);
                history.setLastFailedAttempt(LocalDateTime.now());
                history.setIpAddress(ip);
                history.setDeviceInfo(deviceInfo);
            }

            historyRepository.save(history);

            log.info("[LoginHistory] user={} status={} count={} ip={}",
                    user.getUsername(), status, history.getLoginCount(), ip);

        } catch (Exception e) {
            log.error("[LoginHistory] Failed to record: {}", e.getMessage());
        }
    }

    // ── Helpers — called on the main request thread only ─────────────────

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";

            HttpServletRequest request = attrs.getRequest();

            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            log.debug("Could not resolve client IP: {}", e.getMessage());
            return "unknown";
        }
    }

    private String resolveDeviceInfo() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            return attrs.getRequest().getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}