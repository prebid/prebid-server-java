package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.HttpResponseSender;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BiddersHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(BiddersHandler.class);

    private static final Map<CharSequence, CharSequence> HEADERS = Collections.singletonMap(
            HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

    private final String body;

    public BiddersHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        Objects.requireNonNull(bidderCatalog);
        Objects.requireNonNull(mapper);

        final Set<String> bidderNamesAndAliases = bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .collect(Collectors.toCollection(TreeSet::new));

        body = mapper.encode(bidderNamesAndAliases);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpResponseSender.from(routingContext, logger)
                .headers(HEADERS)
                .body(body)
                .send();
    }
}
