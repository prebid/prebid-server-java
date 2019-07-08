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
import java.util.stream.Stream;

public class BiddersHandler implements Handler<RoutingContext> {

    private final String body;

    public BiddersHandler(BidderCatalog bidderCatalog) {
        Objects.requireNonNull(bidderCatalog);

        final Set<String> bidderNamesAndAliases = Stream.concat(
                bidderCatalog.names().stream()
                        .filter(bidderCatalog::isActive),
                bidderCatalog.aliases().stream()
                        .filter(alias -> bidderCatalog.isActive(bidderCatalog.nameByAlias(alias))))
                .collect(Collectors.toCollection(TreeSet::new));

        body = Json.encode(bidderNamesAndAliases);
    }

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .end(body);
    }
}
