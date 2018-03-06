package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InfoBidderDetailsHandler implements Handler<RoutingContext> {

    private final Map<String, String> bidderInfos;

    public InfoBidderDetailsHandler(BidderCatalog bidderCatalog) {
        bidderInfos = createBidderInfos(Objects.requireNonNull(bidderCatalog));
    }

    /**
     * Returns a map with bidder name as a key and json-encoded bidder info as a value.
     */
    private static Map<String, String> createBidderInfos(BidderCatalog bidderCatalog) {
        return bidderCatalog.names().stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidderName -> Json.encode(bidderCatalog.metaInfoByName(bidderName).info())));
    }

    @Override
    public void handle(RoutingContext context) {
        final String bidderName = context.request().getParam("bidderName");

        if (bidderInfos.containsKey(bidderName)) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(bidderInfos.get(bidderName));
        } else {
            context.response()
                    .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                    .end();
        }
    }
}
