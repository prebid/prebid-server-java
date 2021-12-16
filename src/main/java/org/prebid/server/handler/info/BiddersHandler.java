package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BiddersHandler implements Handler<RoutingContext> {

    private static final String ENABLED_ONLY_PARAM = "enabledonly";
    private final BidderCatalog bidderCatalog;
    private final JacksonMapper mapper;

    public BiddersHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
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
        final boolean enabledOnly = enabledOnlyFromQueryStringParams(routingContext);

        final Set<String> bidderNamesAndAliases = enabledOnly
                ? bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .collect(Collectors.toSet())
                : bidderCatalog.names();

        return mapper.encodeToString(new TreeSet<>(bidderNamesAndAliases));
    }

    private static boolean enabledOnlyFromQueryStringParams(RoutingContext routingContext) {
        final String enabledOnlyParam = routingContext.queryParams().get(ENABLED_ONLY_PARAM);

        if (enabledOnlyParam != null && !StringUtils.equalsAnyIgnoreCase(enabledOnlyParam, "true", "false")) {
            throw new IllegalArgumentException("Invalid value for 'enabledonly' query param, must be of boolean type");
        }

        return Boolean.parseBoolean(enabledOnlyParam);
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, Endpoint.info_bidders,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .setStatusCode(status.code())
                        .end(body));
    }
}
