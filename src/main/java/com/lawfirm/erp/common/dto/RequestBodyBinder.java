package com.lawfirm.erp.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a request body that may or may not wear the {@code {"data": …}} envelope.
 *
 * <p>Most of this API binds {@code ApiRequest<T>}, but the user-management screens post their
 * payload bare — and two of them post nothing at all (the "reset password" confirmation and
 * "bulk deactivate"). The envelope binding turned those into a 400 before the controller ran,
 * so the actions looked like they were not implemented. This binder accepts either shape, and
 * an absent body yields an empty instance for services whose fields are all optional.
 *
 * <p>Field validation stays where it belongs — in the endpoint, with the same messages the
 * {@code @Valid ApiRequest<…>} form produced.
 */
@Component
@RequiredArgsConstructor
public class RequestBodyBinder {

    private final ObjectMapper objectMapper;

    /**
     * Deserializes the raw body into {@code type}, unwrapping {@code data} when it is present.
     *
     * <p>The body arrives as text rather than a {@code JsonNode} on purpose: the web layer here
     * has no converter registered for Jackson tree types, so binding a {@code JsonNode} parameter
     * fails before this class is reached.
     */
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
