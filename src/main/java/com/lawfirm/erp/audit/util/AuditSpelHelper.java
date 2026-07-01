package com.lawfirm.erp.audit.util;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper to evaluate SpEL expressions for audit annotations.
 * Simple and reusable.
 */
public class AuditSpelHelper {

    private static final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluates a SpEL expression against the given context.
     * @param expression SpEL expression (e.g., "#result.id")
     * @param rootObject the object to evaluate against (usually the method result or parameter)
     * @param variables additional variables (e.g., method parameters)
     * @return the evaluated value, or null if evaluation fails
     */
    public static Object evaluate(String expression, Object rootObject, Map<String, Object> variables) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
            variables.forEach(context::setVariable);
            return parser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            // If SpEL fails, return null and let the caller handle it
            return null;
        }
    }

    /**
     * Builds a map of method parameter names to their values.
     */
    public static Map<String, Object> buildParameterMap(Method method, Object[] args) {
        Map<String, Object> params = new HashMap<>();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            params.put(parameters[i].getName(), args[i]);
        }
        return params;
    }
}