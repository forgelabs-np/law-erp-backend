package com.lawfirm.erp.common;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.modules.casemanagement.enums.TimelineEventType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hibernate writes enum columns with a CHECK constraint when it creates a table, but
 * {@code ddl-auto=update} never ALTERs an existing constraint. Every new enum value therefore
 * needs a SQL migration, or a database created earlier rejects the row at runtime — exactly
 * what happened when {@code DOCUMENT_UPLOADED} failed to insert into {@code matter_timeline}.
 *
 * <p>These tests fail when a value is added without a migration that mentions it, so the
 * trap is caught at build time instead of on the live PostgreSQL.
 */
class MigrationConstraintDriftTest {

    private String allMigrations() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");
        assertTrue(resources.length > 0, "no db/migration/*.sql found on the classpath");
        return Arrays.stream(resources).map(resource -> {
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("could not read " + resource, e);
            }
        }).collect(Collectors.joining("\n"));
    }

    @Test
    void everyTimelineEventTypeIsCoveredByAMigration() throws Exception {
        String sql = allMigrations();
        for (TimelineEventType type : TimelineEventType.values()) {
            assertTrue(sql.contains("'" + type.name() + "'"),
                    "TimelineEventType." + type.name() + " is missing from db/migration — a "
                            + "pre-existing matter_timeline_event_type_check would reject it");
        }
    }

    @Test
    void everyPermissionActionIsCoveredByAMigration() throws Exception {
        String sql = allMigrations();
        for (PermissionAction action : PermissionAction.values()) {
            assertTrue(sql.contains("'" + action.name() + "'"),
                    "PermissionAction." + action.name() + " is missing from db/migration — a "
                            + "pre-existing permissions_action_check would reject it");
        }
    }
}
