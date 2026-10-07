package com.lawfirm.erp.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestBodyBinder {

    private final ObjectMapper objectMapper;

    public <T> T bind(String body, Class<T> type) {

        if (body == null || body.isBlank()) {
            return newInstance(type);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode payload = root != null && root.hasNonNull("data") ? root.get("data") : root;

            if (payload == null || payload.isNull() || (payload.isObject() && payload.isEmpty())) {
                return newInstance(type);
            }

            return objectMapper.treeToValue(payload, type);
        } catch (Exception e) {
            throw new BusinessRuleException("Malformed request body: " + e.getMessage());
        }
    }

    private <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new BusinessRuleException(
                    "A request body is required for " + type.getSimpleName());
        }
    }
}
