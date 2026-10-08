package com.lawfirm.erp.integration;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.customer.entity.CustomerProfile;
import com.lawfirm.erp.customer.repository.CustomerProfileRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.entity.AuditLog;
import com.lawfirm.erp.modules.audit.repository.AuditLogRepository;
import com.lawfirm.erp.rbac.entity.Role;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executes the N+1 fixes against a real database (not mocks) and asserts the associations come back
 * initialized. If someone drops an {@code @EntityGraph}, the association becomes a lazy proxy and
 * these assertions fail — so this is the regression guard for the fetch joins.
 */
@Transactional
class FetchGraphQueryTest extends BaseIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String ADMIN_USERNAME = "fetchgraphadmin";
    private static final String CLIENT_USERNAME = "fetchgraphclient";

    /** Detaches everything built so far so the query under test has to load its own state. */
    private void detach() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findAllFirmAdmins() fetches the firm instead of leaving a proxy")
    void findAllFirmAdmins_fetchesFirm() {
        Firm firm = createFirm("FGADM", "Fetch Graph Firm");
        Role adminRole = getSystemRole("FIRM_ADMIN");
        User admin = createUser(ADMIN_USERNAME, ADMIN_USERNAME + "@fg.test", "pw",
                UserType.FIRM, adminRole, firm);
        UUID adminId = admin.getId();
        detach();

        User found = userRepository.findAllFirmAdmins().stream()
                .filter(u -> adminId.equals(u.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(Hibernate.isInitialized(found.getFirm()),
                "firm must be fetched by findAllFirmAdmins(), not a lazy proxy");
        assertEquals("Fetch Graph Firm", found.getFirm().getName());
    }

    @Test
    @DisplayName("findAllWithRoleAndFirmPaged() fetches role and firm")
    void findAllWithRoleAndFirmPaged_fetchesRoleAndFirm() {
        Firm firm = createFirm("FGPAGE", "Paged Firm");
        Role adminRole = getSystemRole("FIRM_ADMIN");
        User admin = createUser(ADMIN_USERNAME + "2", ADMIN_USERNAME + "2@fg.test", "pw",
                UserType.FIRM, adminRole, firm);
        UUID adminId = admin.getId();
        detach();

        Page<User> page = userRepository.findAllWithRoleAndFirmPaged(null, null, null, PageRequest.of(0, 100));
        User found = page.getContent().stream()
                .filter(u -> adminId.equals(u.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(Hibernate.isInitialized(found.getFirm()),
                "firm must be fetched by the paged query, not a lazy proxy");
        assertEquals("Paged Firm", found.getFirm().getName());
        assertTrue(Hibernate.isInitialized(found.getRole()), "role must be initialized");
        assertEquals("FIRM_ADMIN", found.getRole().getRoleCode());
    }

    @Test
    @DisplayName("CustomerProfileRepository.findMatches() fetches the matched user")
    void findMatches_fetchesUser() {
        Firm firm = createFirm("FGMATCH", "Match Firm");
        Role clientRole = getSystemRole("CLIENT");
        User client = createUser(CLIENT_USERNAME, CLIENT_USERNAME + "@fg.test", "pw",
                UserType.CLIENT, clientRole, firm);

        CustomerProfile profile = CustomerProfile.builder()
                .user(client)
                .firm(firm)
                .address("Kathmandu")
                .build();
        customerProfileRepository.save(profile);
        detach();

        List<CustomerProfile> matches = customerProfileRepository.findMatches(
                firm.getId(), client.getFullName(), null, null);

        assertFalse(matches.isEmpty(), "the client should match on full name");
        CustomerProfile match = matches.stream()
                .filter(c -> "Kathmandu".equals(c.getAddress()))
                .findFirst()
                .orElseThrow();
        assertTrue(Hibernate.isInitialized(match.getUser()),
                "user must be fetched by findMatches(), not a lazy proxy");
        assertEquals(client.getFullName(), match.getUser().getFullName());
    }

    @Test
    @DisplayName("searchPaged() filters, orders and pages in the database")
    void searchPaged_pagesInTheDatabase() {
        Firm firm = createFirm("FGSEARCH", "Search Firm");
        Role advocate = getSystemRole("ADVOCATE");
        for (int i = 1; i <= 3; i++) {
            createUser("pageduser" + i, "pageduser" + i + "@fg.test", "pw",
                    UserType.FIRM_USER, advocate, firm);
        }
        createUser("someoneelse", "someoneelse@fg.test", "pw",
                UserType.FIRM_USER, advocate, firm);
        detach();

        Page<User> firstPage = userRepository.searchPaged(
                firm.getId(), UserType.FIRM_USER, null, true, "pageduser", PageRequest.of(0, 2));
        assertEquals(3L, firstPage.getTotalElements(), "only the matching users are counted");
        assertEquals(2, firstPage.getContent().size(), "the database applies the page size");

        Page<User> secondPage = userRepository.searchPaged(
                firm.getId(), UserType.FIRM_USER, null, true, "pageduser", PageRequest.of(1, 2));
        assertEquals(3L, secondPage.getTotalElements());
        assertEquals(1, secondPage.getContent().size());

        Page<User> wrongType = userRepository.searchPaged(
                firm.getId(), UserType.CLIENT, null, null, null, PageRequest.of(0, 10));
        assertEquals(0L, wrongType.getTotalElements(), "userType filter is applied in SQL");
    }

    @Test
    @DisplayName("countByFirmAndUser() counts rows without materialising them")
    void countByFirmAndUser_countsRows() {
        Firm firm = createFirm("FGCOUNT", "Count Firm");
        Role advocate = getSystemRole("ADVOCATE");
        User actor = createUser("countactor", "countactor@fg.test", "pw",
                UserType.FIRM_USER, advocate, firm);

        auditLogRepository.save(AuditLog.of(firm.getId(), actor.getId(), "U",
                AuditAction.USER_CREATED, AuditEntity.USER, actor.getId(), "created", null));
        detach();

        assertEquals(1L, auditLogRepository.countByFirmAndUser(
                firm.getId(), actor.getId(), LocalDateTime.now().minusDays(1), null));
        assertEquals(0L, auditLogRepository.countByFirmAndUser(
                firm.getId(), actor.getId(), LocalDateTime.now().plusDays(1), null));
    }
}
