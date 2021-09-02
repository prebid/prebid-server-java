package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;

import java.util.Objects;

@EqualsAndHashCode
public class Not implements NonTerminalExpression {

    private final Expression expression;

    public Not(Expression expression) {
        this.expression = Objects.requireNonNull(expression);
    }

    @Override
    public boolean matches(RequestContext context) {
        return !expression.matches(context);
    }
}
