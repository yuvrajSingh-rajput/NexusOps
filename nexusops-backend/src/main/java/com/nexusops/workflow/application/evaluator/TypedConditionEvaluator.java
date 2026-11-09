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
            "CONTAINS",
            "BETWEEN",
            "IS_EMPTY"
    );

    /**
     * Evaluates a typed JSON predicate condition against the workflow execution context.
     * Supports single predicates or compound conditions grouped by AND / OR.
     * Returns true if condition is satisfied or if condition is null/empty.
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(Map<String, Object> conditionExpr, Map<String, Object> contextData) {
        if (conditionExpr == null || conditionExpr.isEmpty()) {
            return true; // Unconditional edge
        }

        // Support compound AND / OR grouping
        if (conditionExpr.containsKey("conditions") && conditionExpr.get("conditions") instanceof List<?> list) {
            List<Map<String, Object>> childConditions = (List<Map<String, Object>>) list;
            if (childConditions.isEmpty()) {
                return true;
            }
            String logicalOp = conditionExpr.getOrDefault("logicalOperator", "AND").toString().trim().toUpperCase();
            if ("OR".equals(logicalOp)) {
                return childConditions.stream().anyMatch(c -> evaluate(c, contextData));
            } else {
                return childConditions.stream().allMatch(c -> evaluate(c, contextData));
            }
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

        if ("IS_EMPTY".equals(operator)) {
            if (actual == null) return true;
            if (actual instanceof Collection<?> coll) return coll.isEmpty();
            if (actual instanceof Map<?, ?> map) return map.isEmpty();
            return actual.toString().trim().isEmpty();
        }

        if ("BETWEEN".equals(operator)) {
            if (actual == null || expected == null || !isNumeric(actual)) return false;
            BigDecimal actualNum = toBigDecimal(actual);
            BigDecimal min = null;
            BigDecimal max = null;

            if (expected instanceof List<?> list && list.size() >= 2) {
                if (isNumeric(list.get(0)) && isNumeric(list.get(1))) {
                    min = toBigDecimal(list.get(0));
                    max = toBigDecimal(list.get(1));
                }
            } else if (expected instanceof Map<?, ?> map) {
                Object minObj = map.get("min");
                Object maxObj = map.get("max");
                if (minObj != null && maxObj != null && isNumeric(minObj) && isNumeric(maxObj)) {
                    min = toBigDecimal(minObj);
                    max = toBigDecimal(maxObj);
                }
            } else if (expected instanceof String str && str.contains(",")) {
                String[] parts = str.split(",");
                if (parts.length >= 2 && isNumeric(parts[0].trim()) && isNumeric(parts[1].trim())) {
                    min = toBigDecimal(parts[0].trim());
                    max = toBigDecimal(parts[1].trim());
                }
            }

            if (min != null && max != null) {
                return actualNum.compareTo(min) >= 0 && actualNum.compareTo(max) <= 0;
            }
            return false;
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

