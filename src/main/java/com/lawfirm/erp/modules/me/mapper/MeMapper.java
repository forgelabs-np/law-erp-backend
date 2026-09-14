package com.lawfirm.erp.modules.me.mapper;

import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.me.dto.MeResponse;
import com.lawfirm.erp.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class MeMapper {

    public MeResponse.FirmInfo toFirmInfo(Firm firm) {
        if (firm == null) return null;
        Long daysRemaining = null;
        if (Boolean.TRUE.equals(firm.getIsTrial()) && firm.getTrialExpiresAt() != null) {
            long days = ChronoUnit.DAYS.between(LocalDateTime.now(), firm.getTrialExpiresAt());
            daysRemaining = Math.max(0, days);
        }
        return MeResponse.FirmInfo.builder()
                .id(firm.getId())
                .name(firm.getName())
                .lawFirmCode(firm.getLawFirmCode())
                .email(firm.getEmail())
                .phone(firm.getPhone())
                .address(firm.getAddress())
                .jurisdiction(firm.getJurisdiction())
                .logoUrl(firm.getLogoUrl())
                .status(firm.getStatus() != null ? firm.getStatus().name() : null)
                .isTrial(Boolean.TRUE.equals(firm.getIsTrial()))
                .trialExpiresAt(firm.getTrialExpiresAt())
                .daysRemaining(daysRemaining)
                .build();
    }

    public MeResponse.RoleInfo toRoleInfo(Role role) {
        if (role == null) return null;
        return MeResponse.RoleInfo.builder()
                .id(role.getId())
                .name(role.getRoleName())
                .code(role.getRoleCode())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .build();
    }
}
