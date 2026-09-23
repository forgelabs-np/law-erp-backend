package com.lawfirm.erp.common.util;

import java.security.SecureRandom;

/**
 * The product's single password policy.
 *
 * <p>Every place a password is set or accepted — self-service change, self-service reset, an
 * admin reset, and client creation — must agree on the same rule. Before this class the client
 * creation form accepted six characters while the change-password endpoints demanded eight, so
 * the same password was valid in one dialog and rejected in another.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 50;

    /** Ambiguous characters (0/O, 1/l/I) are left out — these are read off a screen. */
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%^&*".toCharArray();

    private static final int TEMPORARY_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /**
     * The reason to show the user when {@code password} breaks the policy, or {@code null} when
     * it satisfies it.
     */
    public static String violation(String password) {

        if (password == null || password.isBlank()) {
            return "New password is required";
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "Password must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters";
        }

        return null;
    }

    /** True when no violation() explains the password. */
    public static boolean isAcceptable(String password) {
        return violation(password) == null;
    }

    /**
     * A random password that satisfies the policy, for a reset an administrator issued without
     * choosing one. Guarantees one character from each class so it is never rejected downstream.
     */
    public static String generateTemporary() {

        char[] chars = new char[TEMPORARY_LENGTH];

        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);

        for (int i = 4; i < chars.length; i++) {
            char[] from = switch (i % 4) {
                case 0 -> UPPER;
                case 1 -> LOWER;
                case 2 -> DIGITS;
                default -> SYMBOLS;
            };
            chars[i] = pick(from);
        }

        return new String(chars);
    }

    private static char pick(char[] from) {
        return from[RANDOM.nextInt(from.length)];
    }
}
