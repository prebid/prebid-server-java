package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;

import java.util.Objects;

public class BidderParamHandler implements Handler<RoutingContext> {

    private final BidderParamValidator bidderParamValidator;

    public BidderParamHandler(BidderParamValidator bidderParamValidator) {
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpUtil.executeSafely(routingContext, Endpoint.bidder_params,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                        .end(bidderParamValidator.schemas()));
    }
}
