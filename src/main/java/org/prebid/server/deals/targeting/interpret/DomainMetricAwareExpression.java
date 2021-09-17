package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;

@EqualsAndHashCode
public class DomainMetricAwareExpression implements Expression {

    private final Expression domainFunction;
    private final String lineItemId;

    public DomainMetricAwareExpression(Expression domainFunction, String lineItemId) {
        this.domainFunction = domainFunction;
        this.lineItemId = lineItemId;
    }

    @Override
    public boolean matches(RequestContext requestContext) {
        final boolean matches = domainFunction.matches(requestContext);
        if (matches) {
            requestContext.txnLog().lineItemsMatchedDomainTargeting().add(lineItemId);
        }
        return matches;
    }
}
