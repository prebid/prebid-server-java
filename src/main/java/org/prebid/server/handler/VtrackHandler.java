package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class VtrackHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VtrackHandler.class);

    private static final String ACCOUNT_PARAMETER = "a";

    private final ApplicationSettings applicationSettings;
    private final CacheService cacheService;
    private final BidderCatalog bidderCatalog;
    private final TimeoutFactory timeoutFactory;
    private final long defaultTimeout;

    public VtrackHandler(ApplicationSettings applicationSettings, BidderCatalog bidderCatalog,
                         CacheService cacheService, TimeoutFactory timeoutFactory, long defaultTimeout) {

        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public void handle(RoutingContext context) {
        final String accountId;
        final List<PutObject> vtrackPuts;
        try {
            accountId = accountId(context);
            vtrackPuts = vtrackPuts(context);
        } catch (IllegalArgumentException e) {
            respondWithBadRequest(context, e.getMessage());
            return;
        }

        final Timeout timeout = timeoutFactory.create(defaultTimeout);
        applicationSettings.getAccountById(accountId, timeout)
                .recover(exception -> handleAccountExceptionOrFallback(exception, accountId))
                .setHandler(async -> handleAccountResult(async, context, vtrackPuts, accountId, timeout));
    }

    private static String accountId(RoutingContext context) {
        final String accountId = context.request().getParam(ACCOUNT_PARAMETER);
        if (StringUtils.isEmpty(accountId)) {
            throw new IllegalArgumentException(
                    String.format("Account '%s' is required query parameter and can't be empty", ACCOUNT_PARAMETER));
        }
        return accountId;
    }

    private static List<PutObject> vtrackPuts(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null || body.length() == 0) {
            throw new IllegalArgumentException("Incoming request has no body");
        }

        final BidCacheRequest bidCacheRequest;
        try {
            bidCacheRequest = Json.decodeValue(body, BidCacheRequest.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Failed to parse request body", e);
        }

        final List<PutObject> putObjects = ListUtils.emptyIfNull(bidCacheRequest.getPuts());
        for (PutObject putObject : putObjects) {
            if (StringUtils.isEmpty(putObject.getBidid())) {
                throw new IllegalArgumentException("'bidid' is required field and can't be empty");
            }
            if (StringUtils.isEmpty(putObject.getBidder())) {
                throw new IllegalArgumentException("'bidder' is required field and can't be empty");
            }
        }
        return putObjects;
    }

    /**
     * Returns fallback {@link Account} if account not found or propagate error if fetching failed.
     */
    private static Future<Account> handleAccountExceptionOrFallback(Throwable exception, String accountId) {
        if (exception instanceof PreBidException) {
            return Future.succeededFuture(Account.builder().id(accountId).eventsEnabled(false).build());
        }
        return Future.failedFuture(exception);
    }

    private void handleAccountResult(AsyncResult<Account> asyncAccount, RoutingContext context,
                                     List<PutObject> vtrackPuts, String accountId, Timeout timeout) {
        if (asyncAccount.failed()) {
            respondWithServerError(context, "Error occurred while fetching account", asyncAccount.cause());
        } else {
            // insert impression tracking if account allows events and bidder allows VAST modification
            final Set<String> biddersAllowingVastUpdate = asyncAccount.result().getEventsEnabled()
                    ? biddersAllowingVastUpdate(vtrackPuts)
                    : Collections.emptySet();

            cacheService.cachePutObjects(vtrackPuts, biddersAllowingVastUpdate, accountId, timeout)
                    .setHandler(asyncCache -> handleCacheResult(asyncCache, context));
        }
    }

    /**
     * Returns list of bidders that allow VAST XML modification.
     */
    private Set<String> biddersAllowingVastUpdate(List<PutObject> vtrackPuts) {
        return vtrackPuts.stream()
                .map(PutObject::getBidder)
                .filter(bidderCatalog::isModifyingVastXmlAllowed)
                .collect(Collectors.toSet());
    }

    private static void handleCacheResult(AsyncResult<BidCacheResponse> async, RoutingContext context) {
        if (async.failed()) {
            respondWithServerError(context, "Error occurred while sending request to cache", async.cause());
        } else {
            try {
                respondWith(context, HttpResponseStatus.OK, Json.encode(async.result()));
            } catch (EncodeException e) {
                respondWithServerError(context, "Error occurred while encoding response", e);
            }
        }
    }

    private static void respondWithBadRequest(RoutingContext context, String message) {
        respondWith(context, HttpResponseStatus.BAD_REQUEST, message);
    }

    private static void respondWithServerError(RoutingContext context, String message, Throwable exception) {
        logger.warn(message, exception);
        respondWith(context, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                String.format("%s: %s", message, exception.getMessage()));
    }

    private static void respondWith(RoutingContext context, HttpResponseStatus status, String body) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }
        context.response().setStatusCode(status.code()).end(body);
    }
}
