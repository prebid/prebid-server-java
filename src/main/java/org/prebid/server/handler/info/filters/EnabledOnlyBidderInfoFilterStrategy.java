package org.prebid.server.handler.info.filters;

import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;

import java.util.function.Predicate;

public class EnabledOnlyBidderInfoFilterStrategy implements BidderInfoFilterStrategy {

    private static final String ENABLED_ONLY_PARAM = "enabledonly";

    private final BidderCatalog bidderCatalog;

    public EnabledOnlyBidderInfoFilterStrategy(BidderCatalog bidderCatalog) {
        this.bidderCatalog = bidderCatalog;
    }

    @Override
    public Predicate<String> filter() {
        return bidderCatalog::isActive;
    }

    @Override
    public boolean isApplicable(RoutingContext routingContext) {
        return BidderInfoFilterStrategy.lookUpQueryParamInContext(ENABLED_ONLY_PARAM, routingContext);
    }
}
