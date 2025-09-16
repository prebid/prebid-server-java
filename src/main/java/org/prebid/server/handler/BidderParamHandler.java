package org.prebid.server.handler;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BidderParamHandler implements ApplicationResource {

    private final BidderParamValidator bidderParamValidator;

    public BidderParamHandler(BidderParamValidator bidderParamValidator) {
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.bidder_params.value()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpUtil.executeSafely(routingContext, Endpoint.bidder_params,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                        .end(bidderParamValidator.schemas()));
    }
}
