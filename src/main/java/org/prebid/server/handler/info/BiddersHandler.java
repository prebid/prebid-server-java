package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.TreeSet;

public class BiddersHandler implements Handler<RoutingContext> {

    private final String body;

    public BiddersHandler(BidderCatalog bidderCatalog) {
        body = Json.encode(new TreeSet<>(Objects.requireNonNull(bidderCatalog).names()));
    }

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .end(body);
    }
}
