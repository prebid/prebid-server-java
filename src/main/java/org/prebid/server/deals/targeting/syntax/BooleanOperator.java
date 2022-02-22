package org.prebid.server.deals.targeting.syntax;

import java.util.Arrays;

public enum BooleanOperator {

    AND("$and"),
    OR("$or"),
    NOT("$not");

    private final String value;

    BooleanOperator(String value) {
        this.value = value;
    }

    public static boolean isBooleanOperator(String candidate) {
        return Arrays.stream(BooleanOperator.values()).anyMatch(op -> op.value.equals(candidate));
    }

    public static BooleanOperator fromString(String candidate) {
        for (final BooleanOperator op : values()) {
            if (op.value.equals(candidate)) {
                return op;
            }
        }
        throw new IllegalArgumentException(String.format("Unrecognized boolean operator: %s", candidate));
    }
}
