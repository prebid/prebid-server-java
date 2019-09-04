package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.validation.BidderParamValidator;

import java.util.Objects;

public class BidderParamHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(BidderParamHandler.class);

    private final BidderParamValidator bidderParamValidator;

    public BidderParamHandler(BidderParamValidator bidderParamValidator) {
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    @Override
    public void handle(RoutingContext context) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }
        context.response().end(bidderParamValidator.schemas());
    }
}
