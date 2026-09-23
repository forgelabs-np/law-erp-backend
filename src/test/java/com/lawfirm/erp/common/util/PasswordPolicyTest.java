package com.lawfirm.erp.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One policy for the whole product: the same rule the create-client form, the change-password
 * endpoints and both reset endpoints now share.
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("seven characters is refused - the rule the client form used to let through")
    void tooShortRefused() {
        assertEquals("Password must be 8-50 characters", PasswordPolicy.violation("aaaaaa"));
        assertEquals("Password must be 8-50 characters", PasswordPolicy.violation("aaaaaaa"));
        assertFalse(PasswordPolicy.isAcceptable("aaaaaaa"));
    }

    @Test
    @DisplayName("eight characters is the boundary and passes")
    void boundaryAccepted() {
        assertNull(PasswordPolicy.violation("aaaaaaa1"));
        assertTrue(PasswordPolicy.isAcceptable("aaaaaaa1"));
    }

    @Test
    @DisplayName("blank and null are 'required', not 'too short'")
    void blankIsRequired() {
        assertEquals("New password is required", PasswordPolicy.violation(null));
        assertEquals("New password is required", PasswordPolicy.violation("   "));
    }

    @Test
    @DisplayName("longer than 50 characters is refused")
    void tooLongRefused() {
        assertEquals("Password must be 8-50 characters", PasswordPolicy.violation("a".repeat(51)));
        assertTrue(PasswordPolicy.isAcceptable("a".repeat(50)));
    }

    @Test
    @DisplayName("a generated temporary password always satisfies the policy")
    void generatedTemporaryIsAcceptable() {
        for (int i = 0; i < 50; i++) {
            String generated = PasswordPolicy.generateTemporary();
            assertTrue(PasswordPolicy.isAcceptable(generated), generated);
            assertEquals(12, generated.length());
        }
    }
}
