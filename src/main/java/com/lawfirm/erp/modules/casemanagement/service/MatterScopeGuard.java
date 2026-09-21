package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.ReadScopeGuard;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.entity.MatterParty;
import com.lawfirm.erp.modules.casemanagement.repository.MatterPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One place that answers "may the caller see this matter?".
 *
 * <p>Case management is client-linked through {@code Matter.clientUserId}. Matters created
 * before that column existed carry the client only as a party ({@code isOurClient = true}),
 * so the link is resolved from either source — which also means rows already in production
 * behave correctly without a backfill.
 */
@Component
@RequiredArgsConstructor
public class MatterScopeGuard {

    private final MatterPartyRepository matterPartyRepository;
    private final ReadScopeGuard readScopeGuard;

    /** True when the caller is a client-portal account (must only see its own matters). */
    public boolean isClientScope() {
        return readScopeGuard.isClientScope();
    }

    /** The client a matter belongs to — first-class column first, then a marked party. */
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

    /** Throws unless the caller may see this matter. Staff always pass. */
    public void requireVisible(Matter matter) {
        readScopeGuard.requireOwnClientRecord(ownerClientId(matter), "this matter");
    }

    /** Convenience for callers that already hold a matter id. */
    public void requireVisible(UUID matterId, UUID firmId, String what) {
        if (!isClientScope()) {
            return;
        }
        UUID owner = matterPartyRepository
                .findByMatterIdInAndFirmIdAndOurClientTrue(List.of(matterId), firmId)
                .stream()
                .map(MatterParty::getClientId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        readScopeGuard.requireOwnClientRecord(owner, what);
    }
}
