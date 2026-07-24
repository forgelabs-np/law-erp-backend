package com.lawfirm.erp.auth.security;

import java.text.Normalizer;

public class UsernameGenerator {

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^a-z0-9\\s]", "");
        slug = slug.replaceAll("\\s+", "");
        return slug.isEmpty() ? "user" : slug;
    }

    public static String build(String firmCode, String nameInput) {
        return slugify(firmCode) + "_" + slugify(nameInput);
    }
}