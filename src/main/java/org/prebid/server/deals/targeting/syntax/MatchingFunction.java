package org.prebid.server.deals.targeting.syntax;

import java.util.Arrays;

public enum MatchingFunction {

    MATCHES("$matches"),
    IN("$in"),
    INTERSECTS("$intersects"),
    WITHIN("$within");

    private final String value;

    MatchingFunction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean isMatchingFunction(String candidate) {
        return Arrays.stream(MatchingFunction.values()).anyMatch(op -> op.value.equals(candidate));
    }

    public static MatchingFunction fromString(String candidate) {
        for (final MatchingFunction op : values()) {
            if (op.value.equals(candidate)) {
                return op;
            }
        }
        throw new IllegalArgumentException(String.format("Unrecognized matching function: %s", candidate));
    }
}
