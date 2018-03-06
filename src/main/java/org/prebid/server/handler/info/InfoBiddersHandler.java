package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Objects;
import java.util.TreeSet;

public class InfoBiddersHandler implements Handler<RoutingContext> {

    private final String body;

    public InfoBiddersHandler(BidderCatalog bidderCatalog) {
        body = Json.encode(new TreeSet<>(Objects.requireNonNull(bidderCatalog).names()));
    }

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(body);
    }
}
