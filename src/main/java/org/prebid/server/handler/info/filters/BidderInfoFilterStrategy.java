package org.prebid.server.handler.info.filters;

import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Predicate;

public interface BidderInfoFilterStrategy {

    Predicate<String> filter();

    boolean isApplicable(RoutingContext routingContext);

    static boolean lookUpQueryParamInContext(String queryParamName, RoutingContext routingContext) {
        final String queryParamValue = routingContext.queryParams().get(queryParamName);

        if (queryParamValue != null && !StringUtils.equalsAnyIgnoreCase(queryParamValue, "true", "false")) {
            throw new IllegalArgumentException(
                    "Invalid value for '" + queryParamName + "' query param, must be of boolean type");
        }

        return Boolean.parseBoolean(queryParamValue);
    }

}
