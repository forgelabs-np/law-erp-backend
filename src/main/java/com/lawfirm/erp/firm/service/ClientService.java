package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.dto.firm.request.CreateClientRequest;
import com.lawfirm.erp.dto.firm.response.ClientResponse;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.service.EmailService;
import com.lawfirm.erp.rbac.entity.Role;
import com.lawfirm.erp.rbac.entity.UserRole;
import com.lawfirm.erp.rbac.repository.RoleRepository;
import com.lawfirm.erp.rbac.repository.UserRoleRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final UserRepository userRepository;
    private final FirmRepository firmRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        UUID firmId = getCurrentFirmId();
        Firm firm = firmRepository.findById(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        // Validate uniqueness
        validateUniqueness(firmId, request.getUsername(), request.getEmail(), request.getMobileNo());

        // Get firm-scoped CLIENT role (isSystem = false)
        Role clientRole = roleRepository
                .findByFirmIdAndRoleCode(firmId, "CLIENT")
                .orElseThrow(() -> new BusinessRuleException("CLIENT role not found for this firm. Please contact support."));

        // Create user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .mobileNo(request.getMobileNo())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .firm(firm)
                .role(clientRole)
                .userType(UserType.CLIENT)
                .portalAccessEnabled(request.getPortalAccessEnabled() != null ? request.getPortalAccessEnabled() : false)
                .isEmailVerified(true)
                .isMobileVerified(true)
                .isBlocked(false)
                .loginAttempts(0)
                .build();
        user.setActive(true);
        user = userRepository.save(user);

        // Assign role
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(clientRole)
                .build();
        userRoleRepository.save(userRole);

        // Audit: Client created
        auditService.log(
                AuditAction.CLIENT_CREATED,
                AuditEntity.CLIENT,
                user.getId(),
                "Client created: " + user.getFullName() + " (" + user.getUsername() + ") in firm: " + firm.getLawFirmCode()
        );

        log.info("Client created: {} in firm {}", user.getUsername(), firm.getLawFirmCode());

        // Send welcome email (async, non-blocking)
        UUID currentUserId = currentUserResolver.getCurrentUserId();
        emailService.sendWelcomeClient(
                firmId,
                currentUserId,
                user.getEmail(),
                user.getFullName(),
                user.getUsername(),
                request.getPassword(), // raw password before encoding
                firm.getName()
        );

        return toResponse(user);
    }

    public PagedResponse<ClientResponse> getAllClients(int page, int size) {
        UUID firmId = getCurrentFirmId();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<User> userPage = userRepository.findByFirmIdAndUserTypePaged(
                firmId,
                UserType.CLIENT,
                pageable
        );

        List<ClientResponse> content = userPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.of(userPage, content);
    }

    public ClientResponse getClientById(UUID clientId) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(clientId, firmId);
        return toResponse(user);
    }

    @Transactional
    public ClientResponse togglePortalAccess(UUID clientId, Boolean portalAccessEnabled) {
        UUID firmId = getCurrentFirmId();
        User user = getUserValidated(clientId, firmId);

        boolean oldValue = user.getPortalAccessEnabled() != null && user.getPortalAccessEnabled();
        user.setPortalAccessEnabled(portalAccessEnabled);
        user = userRepository.save(user);

        auditService.log(
                portalAccessEnabled ? AuditAction.CLIENT_PORTAL_ENABLED : AuditAction.CLIENT_PORTAL_DISABLED,
                AuditEntity.CLIENT,
                user.getId(),
                (portalAccessEnabled ? "Enabled" : "Disabled") + " portal access for client: " + user.getFullName()
        );

        log.info("Portal access for client {} set to {}", user.getUsername(), portalAccessEnabled);

        return toResponse(user);
    }

    private UUID getCurrentFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) {
            throw new ForbiddenException("This action requires a firm context");
        }
        return firmId;
    }

    private void validateUniqueness(UUID firmId, String username, String email, String mobileNo) {
        if (userRepository.existsByUsernameAndFirmId(username, firmId)) {
            throw new DuplicateResourceException("Username already exists in your firm");
        }
        if (userRepository.existsByEmailAndFirmId(email, firmId)) {
            throw new DuplicateResourceException("Email already exists in your firm");
        }
        if (userRepository.existsByMobileNoAndFirmId(mobileNo, firmId)) {
            throw new DuplicateResourceException("Mobile number already exists in your firm");
        }
    }

    private User getUserValidated(UUID userId, UUID firmId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getFirm() == null || !user.getFirm().getId().equals(firmId)) {
            throw new ForbiddenException("User does not belong to your firm");
        }

        if (user.getUserType() != UserType.CLIENT) {
            throw new BusinessRuleException("User is not a client");
        }

        return user;
    }

    private ClientResponse toResponse(User user) {
        return ClientResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNo(user.getMobileNo())
                .fullName(user.getFullName())
                .userType(user.getUserType())
                .isActive(user.isActive())
                .portalAccessEnabled(user.getPortalAccessEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}