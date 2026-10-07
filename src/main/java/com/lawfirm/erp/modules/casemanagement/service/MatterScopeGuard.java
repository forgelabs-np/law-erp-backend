package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MatterScopeGuard {

    private final MatterPartyRepository matterPartyRepository;
    private final MatterRepository matterRepository;
    private final ReadScopeGuard readScopeGuard;

    public boolean isClientScope() {
        return readScopeGuard.isClientScope();
    }

    public UUID ownerClientId(Matter matter) {
        if (matter.getClientUserId() != null) {
            return matter.getClientUserId();
        }
        return matterPartyRepository
                .findByMatterIdInAndFirmIdAndOurClientTrue(List.of(matter.getId()), matter.getFirmId())
                .stream()
                .map(MatterParty::getClientId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public void requireVisible(Matter matter) {
        readScopeGuard.requireOwnClientRecord(ownerClientId(matter), "this matter");
    }

    public void requireVisible(UUID matterId, UUID firmId, String what) {
        if (!isClientScope()) {
            return;
        }
        // Load once and reject a matter outside the caller's firm *before* touching ownership, so a
        // client can never use the id to probe another firm's matter.
        Matter matter = matterRepository.findById(matterId).orElse(null);
        if (matter == null || (firmId != null && !firmId.equals(matter.getFirmId()))) {
            readScopeGuard.requireOwnClientRecord(null, what);
            return;
        }

        UUID owner = matter.getClientUserId();
        if (owner == null) {
            owner = matterPartyRepository
                    .findByMatterIdInAndFirmIdAndOurClientTrue(List.of(matterId), firmId)
                    .stream()
                    .map(MatterParty::getClientId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        readScopeGuard.requireOwnClientRecord(owner, what);
    }
}
