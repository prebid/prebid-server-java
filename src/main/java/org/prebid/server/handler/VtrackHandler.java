package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.execution.TimeoutFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VtrackHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VtrackHandler.class);

    private static final String ACCOUNT_REQUEST_PARAMETER = "a";

    private final CacheService cacheService;
    private final BidderCatalog bidderCatalog;
    private final TimeoutFactory timeoutFactory;
    private final long defaultTimeout;

    public VtrackHandler(CacheService cacheService, BidderCatalog bidderCatalog, TimeoutFactory timeoutFactory,
                         long defaultTimeout) {
        this.cacheService = Objects.requireNonNull(cacheService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public void handle(RoutingContext context) {
        final List<PutObject> vtrackPuts;
        try {
            vtrackPuts = parsePuts(context.getBody());
        } catch (IllegalArgumentException e) {
            respondWith(context, HttpResponseStatus.BAD_REQUEST.code(), e.getMessage());
            return;
        }

        String accountId = null;
        final List<String> updatableBidders = biddersWithUpdatableVast(vtrackPuts);
        if (!updatableBidders.isEmpty()) {
            try {
                accountId = accountId(context);
            } catch (IllegalArgumentException e) {
                respondWith(context, HttpResponseStatus.BAD_REQUEST.code(), e.getMessage());
                return;
            }
        }

        cacheService.cachePutObjects(vtrackPuts, updatableBidders, accountId, timeoutFactory.create(defaultTimeout))
                .setHandler(bidCacheResponseResult -> handleCacheResult(context, bidCacheResponseResult));
    }

    private static List<PutObject> parsePuts(Buffer body) {
        if (body == null || body.length() == 0) {
            throw new IllegalArgumentException("Incoming request has no body");
        }

        try {
            final List<PutObject> puts = Json.decodeValue(body, BidCacheRequest.class).getPuts();
            return puts == null ? Collections.emptyList() : puts;
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Failed to parse /vtrack request body", e);
        }
    }

    private static String accountId(RoutingContext context) {
        final String accountId = context.request().getParam(ACCOUNT_REQUEST_PARAMETER);
        if (accountId == null) {
            throw new IllegalArgumentException("Request must contain 'a'=accountId parameter");
        }
        return accountId;
    }

    private List<String> biddersWithUpdatableVast(List<PutObject> vtrackPuts) {
        return vtrackPuts.stream()
                .map(PutObject::getBidder)
                .distinct()
                .filter(bidderCatalog::isModifyingVastXmlAllowed)
                .collect(Collectors.toList());
    }

    private void handleCacheResult(RoutingContext context, AsyncResult<BidCacheResponse> bidCacheResponseResult) {
        if (bidCacheResponseResult.succeeded()) {
            respondWithCache(context, bidCacheResponseResult.result());
        } else {
            final String message = bidCacheResponseResult.cause().getMessage();
            respondWith(context, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), message);
        }
    }

    private void respondWithCache(RoutingContext context, BidCacheResponse bidCacheResponse) {
        try {
            respondWith(context, HttpResponseStatus.OK.code(), Json.mapper.writeValueAsString(bidCacheResponse));
        } catch (JsonProcessingException e) {
            logger.error("/vtrack Critical error when trying to marshal cache response: {0}", e.getMessage());
            respondWith(context, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), e.getMessage());
        }
    }

    private static void respondWith(RoutingContext context, int status, String body) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }
        context.response().setStatusCode(status).end(body);
    }
}

