package com.lawfirm.erp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.*;

public class HeaderWrapper extends HttpServletRequestWrapper {
    private final Map<String, String> customHeaders;

    public HeaderWrapper(HttpServletRequest request, Map<String, String> headers) {
        super(request);
        this.customHeaders = new HashMap<>(headers);
    }

    @Override
    public String getHeader(String name) {
        // Custom header takes priority, then fall through to original
        return customHeaders.getOrDefault(name, super.getHeader(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        // Combine original header names with custom ones
        Set<String> names = new HashSet<>();
        names.addAll(Collections.list(super.getHeaderNames()));
        names.addAll(customHeaders.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> values = new ArrayList<>();

        // Add custom header if present
        if (customHeaders.containsKey(name)) {
            values.add(customHeaders.get(name));
        }

        // Add original headers
        Enumeration<String> originalHeaders = super.getHeaders(name);
        while (originalHeaders.hasMoreElements()) {
            values.add(originalHeaders.nextElement());
        }

        return Collections.enumeration(values);
    }
}