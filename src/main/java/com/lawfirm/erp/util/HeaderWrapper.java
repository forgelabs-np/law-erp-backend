package com.lawfirm.erp.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class HeaderWrapper extends HttpServletRequestWrapper {
    private final Map<String, String> customHeaders;

    public HeaderWrapper(HttpServletRequest request, Map<String, String> headers) {
        super(request);
        this.customHeaders = new HashMap<>(headers);
    }

    @Override
    public String getHeader(String name) {
        String headerValue = customHeaders.get(name);
        if (headerValue != null) {
            return headerValue;
        }
        return ((HttpServletRequest) getRequest()).getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(customHeaders.keySet());
    }
}
