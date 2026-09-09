package com.lawfirm.erp.modules.notification.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.UnauthorizedException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.modules.notification.dto.SendBroadcastRequest;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Firm-admin announcements. Sends synchronously through the orchestrator
 * (not via the async bus) so the admin sees fan-out results immediately.
 * The event always carries the caller's firmId — broadcasts never cross firms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final NotificationOrchestrator orchestrator;
    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;

    public void send(SendBroadcastRequest request) {
        UUID senderId = currentUserResolver.getCurrentUserId();
        if (senderId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new BusinessRuleException("Broadcasts can only be sent from within a firm");
        }

        String audience = request.getAudience() == null ? "" : request.getAudience().trim();
        NotificationEvent event;
        if ("ALL".equalsIgnoreCase(audience)) {
            event = new NotificationEvent(firmId, null, null, true,
                    NotificationType.ANNOUNCEMENT, "FIRM", firmId, null,
                    java.util.Map.of(
                            "title", request.getTitle(),
                            "body", request.getBody(),
                            "sentBy", senderId));
        } else if (audience.matches("[A-Z_]{2,40}")) {
            // Validate the role actually has users in this firm — a typo'd
            // audience must fail loudly, not silently no-op.
            List<UUID> targets = userRepository.findUserIdsByFirmIdAndRoleCode(firmId, audience);
            if (targets.isEmpty()) {
                throw new BusinessRuleException(
                        "Audience role '" + audience + "' has no users in your firm");
            }
            event = NotificationEvent.toRole(firmId, audience,
                    NotificationType.ANNOUNCEMENT, "FIRM", firmId,
                    java.util.Map.of(
                            "title", request.getTitle(),
                            "body", request.getBody(),
                            "sentBy", senderId));
        } else {
            throw new BusinessRuleException(
                    "Audience must be ALL or a role code (letters/underscores), got: " + audience);
        }

        orchestrator.process(event);
        log.info("Broadcast sent by {} to {} in firm {}", senderId, audience, firmId);
    }
}
