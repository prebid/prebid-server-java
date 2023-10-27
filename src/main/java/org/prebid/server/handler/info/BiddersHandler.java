package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.handler.info.filters.BidderInfoFilterStrategy;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BiddersHandler implements Handler<RoutingContext> {

    private final BidderCatalog bidderCatalog;
    private final List<BidderInfoFilterStrategy> filterStrategies;
    private final JacksonMapper mapper;

    public BiddersHandler(BidderCatalog bidderCatalog,
                          List<BidderInfoFilterStrategy> filterStrategies,
                          JacksonMapper mapper) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.filterStrategies = Objects.requireNonNull(filterStrategies);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            respondWith(routingContext, HttpResponseStatus.OK, resolveBodyFromContext(routingContext));
        } catch (IllegalArgumentException e) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String resolveBodyFromContext(RoutingContext routingContext) {
        final Set<String> allBidders = bidderCatalog.names();
        final Set<String> bidderNames = filterStrategies.stream()
                .filter(strategy -> strategy.isApplicable(routingContext))
                .map(BidderInfoFilterStrategy::filter)
                .reduce(Predicate::and)
                .map(filter -> allBidders.stream().filter(filter).collect(Collectors.toSet()))
                .orElse(allBidders);

        return mapper.encodeToString(new TreeSet<>(bidderNames));
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, Endpoint.info_bidders,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .setStatusCode(status.code())
                        .end(body));
    }
}
