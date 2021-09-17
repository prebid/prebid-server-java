package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.Objects;
import java.util.function.BiFunction;

@EqualsAndHashCode
public class Matches implements TerminalExpression {

    private static final String WILDCARD = "*";

    private final TargetingCategory category;

    private BiFunction<String, String, Boolean> method;

    private String value;

    public Matches(TargetingCategory category, String value) {
        this.category = Objects.requireNonNull(category);
        this.method = resolveMethod(Objects.requireNonNull(value));
        this.value = value.replaceAll("\\*", "").toLowerCase();
    }

    @Override
    public boolean matches(RequestContext context) {
        final String valueToMatch = context.lookupString(category);
        return valueToMatch != null && method.apply(valueToMatch.toLowerCase(), value);
    }

    private static BiFunction<String, String, Boolean> resolveMethod(String value) {
        if (value.startsWith(WILDCARD) && value.endsWith(WILDCARD)) {
            return String::contains;
        } else if (value.startsWith(WILDCARD)) {
            return String::endsWith;
        } else if (value.endsWith(WILDCARD)) {
            return String::startsWith;
        } else {
            return String::equals;
        }
    }
}
