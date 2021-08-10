package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.TreeSet;

public class BiddersHandler implements Handler<RoutingContext> {

    private final String body;

    public BiddersHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        Objects.requireNonNull(bidderCatalog);
        Objects.requireNonNull(mapper);

        body = mapper.encode(new TreeSet<>(bidderCatalog.names()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpUtil.executeSafely(routingContext, Endpoint.info_bidders,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .end(body));
    }
}
