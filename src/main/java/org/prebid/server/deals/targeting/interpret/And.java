package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode
public class And implements NonTerminalExpression {

    private final List<Expression> expressions;

    public And(List<Expression> expressions) {
        this.expressions = Collections.unmodifiableList(expressions);
    }

    @Override
    public boolean matches(RequestContext context) {
        for (final Expression expression : expressions) {
            if (!expression.matches(context)) {
                return false;
            }
        }
        return true;
    }
}
