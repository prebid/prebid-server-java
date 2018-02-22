package org.prebid.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.validation.BidderParamValidator;

import java.util.Objects;

public class BidderParamHandler implements Handler<RoutingContext> {

    private final BidderParamValidator bidderParamValidator;

    public BidderParamHandler(BidderParamValidator bidderParamValidator) {
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    @Override
    public void handle(RoutingContext context) {
        context.response().end(bidderParamValidator.schemas());
    }
}
