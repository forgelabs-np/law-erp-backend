package com.lawfirm.erp.modules.audit.util;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class AuditSpelHelper {

    private static final ExpressionParser parser = new SpelExpressionParser();

    public static Object evaluate(String expression, Object rootObject, Map<String, Object> variables) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
            variables.forEach(context::setVariable);
            // annotation's javadoc). Without this binding the variable is undefined, SpEL throws
            context.setVariable("result", rootObject);
            return parser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Object> buildParameterMap(Method method, Object[] args) {
        Map<String, Object> params = new HashMap<>();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            params.put(parameters[i].getName(), args[i]);
        }
        return params;
    }
}