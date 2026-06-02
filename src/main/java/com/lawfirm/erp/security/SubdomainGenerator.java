package com.lawfirm.erp.security;

import java.text.Normalizer;

public class SubdomainGenerator {

    public static String generate(String input) {
        String normalized = Normalizer.normalize(input.toLowerCase(), Normalizer.Form.NFD);
        String slug = normalized.replaceAll("[^a-z0-9\\s-]", "");
        slug = slug.replaceAll("[\\s-]+", "-");
        slug = slug.replaceAll("^-|-$", "");

        if (slug.isEmpty()) {
            slug = "lawyer" + System.currentTimeMillis();
        }

        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }
}
