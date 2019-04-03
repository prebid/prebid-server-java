package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BiddersHandler implements Handler<RoutingContext> {

    private final String body;

    public BiddersHandler(BidderCatalog bidderCatalog) {
        final Set<String> activeBidders = Objects.requireNonNull(bidderCatalog).names().stream()
                .filter(bidderCatalog::isActive)
                .collect(Collectors.toSet());
        body = Json.encode(new TreeSet<>(activeBidders));
    }

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .end(body);
    }
}
