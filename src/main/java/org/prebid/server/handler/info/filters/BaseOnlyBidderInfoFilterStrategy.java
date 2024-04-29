package org.prebid.server.handler.info.filters;

import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;

import java.util.function.Predicate;

public class BaseOnlyBidderInfoFilterStrategy implements BidderInfoFilterStrategy {

    private static final String BASE_ONLY_PARAM = "baseadaptersonly";

    private final BidderCatalog bidderCatalog;

    public BaseOnlyBidderInfoFilterStrategy(BidderCatalog bidderCatalog) {
        this.bidderCatalog = bidderCatalog;
    }

    @Override
    public Predicate<String> filter() {
        final Predicate<String> filter = bidderCatalog::isAlias;
        return filter.negate();
    }

    @Override
    public boolean isApplicable(RoutingContext routingContext) {
        return BidderInfoFilterStrategy.lookUpQueryParamInContext(BASE_ONLY_PARAM, routingContext);
    }
}
