package org.prebid.server.deals.targeting.interpret;

import org.prebid.server.deals.targeting.RequestContext;

public interface Expression {

    boolean matches(RequestContext context);
}
