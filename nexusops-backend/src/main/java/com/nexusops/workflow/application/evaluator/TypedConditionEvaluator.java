package com.nexusops.workflow.application.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class TypedConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TypedConditionEvaluator.class);

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "EQUALS",
            "NOT_EQUALS",
            "GREATER_THAN",
            "GREATER_THAN_OR_EQUAL",
            "LESS_THAN",
            "LESS_THAN_OR_EQUAL",
            "IN",
            "CONTAINS"
    );

    /**
     * Evaluates a typed JSON predicate condition against the workflow execution context.
     * Returns true if condition is satisfied or if condition is null/empty.
     */
    public boolean evaluate(Map<String, Object> conditionExpr, Map<String, Object> contextData) {
        if (conditionExpr == null || conditionExpr.isEmpty()) {
            return true; // Unconditional edge
        }

        Object fieldPathObj = conditionExpr.get("field");
        Object operatorObj = conditionExpr.get("operator");
        Object targetValue = conditionExpr.get("value");

        if (fieldPathObj == null || operatorObj == null) {
            log.warn("Malformed condition expression: missing 'field' or 'operator': {}", conditionExpr);
            return false;
        }

        String fieldPath = fieldPathObj.toString().trim();
        String operator = operatorObj.toString().trim().toUpperCase();

        if (!ALLOWED_OPERATORS.contains(operator)) {
            log.error("Rejected unauthorized operator '{}' in condition evaluation", operator);
            return false;
        }

        Object actualValue = resolveField(fieldPath, contextData);

        return compare(actualValue, operator, targetValue);
    }

    private Object resolveField(String path, Map<String, Object> context) {
        if (context == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = context;

        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private boolean compare(Object actual, String operator, Object expected) {
        if ("EQUALS".equals(operator)) {
            if (actual == null && expected == null) return true;
            if (actual == null || expected == null) return false;
            if (isNumeric(actual) && isNumeric(expected)) {
                return toBigDecimal(actual).compareTo(toBigDecimal(expected)) == 0;
            }
            return Objects.equals(actual.toString(), expected.toString());
        }

        if ("NOT_EQUALS".equals(operator)) {
            return !compare(actual, "EQUALS", expected);
        }

        if (actual == null || expected == null) {
            return false;
        }

        if (isNumeric(actual) && isNumeric(expected)) {
            BigDecimal actualNum = toBigDecimal(actual);
            BigDecimal expectedNum = toBigDecimal(expected);
            int comp = actualNum.compareTo(expectedNum);

            return switch (operator) {
                case "GREATER_THAN" -> comp > 0;
                case "GREATER_THAN_OR_EQUAL" -> comp >= 0;
                case "LESS_THAN" -> comp < 0;
                case "LESS_THAN_OR_EQUAL" -> comp <= 0;
                default -> false;
            };
        }

        if ("CONTAINS".equals(operator)) {
            if (actual instanceof Collection<?> coll) {
                return coll.contains(expected);
            }
            return actual.toString().contains(expected.toString());
        }

        if ("IN".equals(operator)) {
            if (expected instanceof Collection<?> coll) {
                return coll.contains(actual) || coll.contains(actual.toString());
            }
            return false;
        }

        return false;
    }

    private boolean isNumeric(Object obj) {
        if (obj instanceof Number) return true;
        if (obj instanceof String str) {
            try {
                new BigDecimal(str);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) return bd;
        return new BigDecimal(obj.toString());
    }
}

