package com.lawfirm.erp.auth.security;

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
        return customHeaders.getOrDefault(name, super.getHeader(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new HashSet<>();
        names.addAll(Collections.list(super.getHeaderNames()));
        names.addAll(customHeaders.keySet());
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        // A custom header overrides the original entirely (same contract as getHeader);
        // appending the original would return the injected value twice.
        if (customHeaders.containsKey(name)) {
            return Collections.enumeration(List.of(customHeaders.get(name)));
        }
        return super.getHeaders(name);
    }
}