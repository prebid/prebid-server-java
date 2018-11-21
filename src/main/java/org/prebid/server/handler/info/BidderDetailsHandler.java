package org.prebid.server.handler.info;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BidderDetailsHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(BidderDetailsHandler.class);

    private final Map<String, String> bidderInfos;

    private BidderCatalog bidderCatalog;

    public BidderDetailsHandler(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        bidderInfos = createBidderInfos(bidderCatalog);
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
            final String bidderInfoAsString = bidderInfos.get(bidderName);
            final String response;

            if (bidderCatalog.isAlias(bidderName)) {
                try {
                    // Add alias to the existing json object
                    final ObjectNode node = (ObjectNode) Json.mapper.readTree(bidderInfoAsString);
                    node.set("aliasOf", new TextNode(bidderCatalog.nameByAlias(bidderName)));
                    response = Json.encode(node);
                } catch (IOException e) {
                    logger.warn("Error occurred while parsing bidder info for {0}", e, bidderName);
                    context.response()
                            .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                            .end("Parsing bidder info error");
                    return;
                }
            } else {
                response = bidderInfoAsString;
            }

            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(response);
        } else {
            context.response()
                    .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                    .end();
        }
    }
}
