package org.prebid.server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
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
import org.prebid.server.events.EventUtil;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class VtrackHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VtrackHandler.class);

    private static final String ACCOUNT_PARAMETER = "a";
    private static final String INTEGRATION_PARAMETER = "int";
    private static final String TYPE_XML = "xml";

    private final long defaultTimeout;
    private final boolean allowUnknownBidder;
    private final boolean modifyVastForUnknownBidder;
    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final CacheService cacheService;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;

    public VtrackHandler(long defaultTimeout,
                         boolean allowUnknownBidder,
                         boolean modifyVastForUnknownBidder,
                         ApplicationSettings applicationSettings,
                         BidderCatalog bidderCatalog,
                         CacheService cacheService,
                         TimeoutFactory timeoutFactory,
                         JacksonMapper mapper) {

        this.defaultTimeout = defaultTimeout;
        this.allowUnknownBidder = allowUnknownBidder;
        this.modifyVastForUnknownBidder = modifyVastForUnknownBidder;
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String accountId;
        final List<PutObject> vtrackPuts;
        final String integration;
        try {
            accountId = accountId(routingContext);
            vtrackPuts = vtrackPuts(routingContext);
            integration = integration(routingContext);
        } catch (IllegalArgumentException e) {
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, e.getMessage());
            return;
        }
        final Timeout timeout = timeoutFactory.create(defaultTimeout);

        applicationSettings.getAccountById(accountId, timeout)
                .recover(exception -> handleAccountExceptionOrFallback(exception, accountId))
                .setHandler(async -> handleAccountResult(async, routingContext, vtrackPuts, accountId, integration,
                        timeout));
    }

    private static String accountId(RoutingContext routingContext) {
        final String accountId = routingContext.request().getParam(ACCOUNT_PARAMETER);
        if (StringUtils.isEmpty(accountId)) {
            throw new IllegalArgumentException(
                    String.format("Account '%s' is required query parameter and can't be empty", ACCOUNT_PARAMETER));
        }
        return accountId;
    }

    private List<PutObject> vtrackPuts(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null || body.length() == 0) {
            throw new IllegalArgumentException("Incoming request has no body");
        }

        final BidCacheRequest bidCacheRequest;
        try {
            bidCacheRequest = mapper.decodeValue(body, BidCacheRequest.class);
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Failed to parse request body", e);
        }

        final List<PutObject> putObjects = ListUtils.emptyIfNull(bidCacheRequest.getPuts());
        for (PutObject putObject : putObjects) {
            validatePutObject(putObject);
        }
        return putObjects;
    }

    private static void validatePutObject(PutObject putObject) {
        if (StringUtils.isEmpty(putObject.getBidid())) {
            throw new IllegalArgumentException("'bidid' is required field and can't be empty");
        }

        if (StringUtils.isEmpty(putObject.getBidder())) {
            throw new IllegalArgumentException("'bidder' is required field and can't be empty");
        }

        if (!StringUtils.equals(putObject.getType(), TYPE_XML)) {
            throw new IllegalArgumentException("vtrack only accepts type xml");
        }

        final JsonNode value = putObject.getValue();
        final String valueAsString = value != null ? value.asText() : null;
        if (!StringUtils.containsIgnoreCase(valueAsString, "<vast")) {
            throw new IllegalArgumentException("vtrack content must be vast");
        }
    }

    public static String integration(RoutingContext routingContext) {
        EventUtil.validateIntegration(routingContext);
        return routingContext.request().getParam(INTEGRATION_PARAMETER);
    }

    /**
     * Returns fallback {@link Account} if account not found or propagate error if fetching failed.
     */
    private static Future<Account> handleAccountExceptionOrFallback(Throwable exception, String accountId) {
        return exception instanceof PreBidException
                ? Future.succeededFuture(Account.builder()
                    .id(accountId)
                    .auction(AccountAuctionConfig.builder()
                            .events(AccountEventsConfig.of(false))
                            .build())
                    .build())
                : Future.failedFuture(exception);
    }

    private void handleAccountResult(AsyncResult<Account> asyncAccount,
                                     RoutingContext routingContext,
                                     List<PutObject> vtrackPuts,
                                     String accountId,
                                     String integration,
                                     Timeout timeout) {

        if (asyncAccount.failed()) {
            respondWithServerError(routingContext, "Error occurred while fetching account", asyncAccount.cause());
        } else {
            // insert impression tracking if account allows events and bidder allows VAST modification
            final Account account = asyncAccount.result();
            final Boolean isEventEnabled = accountEventsEnabled(asyncAccount.result());
            final Set<String> allowedBidders = biddersAllowingVastUpdate(vtrackPuts);
            cacheService.cachePutObjects(vtrackPuts, isEventEnabled, allowedBidders, accountId, integration, timeout)
                    .setHandler(asyncCache -> handleCacheResult(asyncCache, routingContext));
        }
    }

    private static Boolean accountEventsEnabled(Account account) {
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();
        final AccountEventsConfig accountEventsConfig =
                accountAuctionConfig != null ? accountAuctionConfig.getEvents() : null;

        return accountEventsConfig != null ? accountEventsConfig.getEnabled() : null;
    }

    /**
     * Returns list of bidders that allow VAST XML modification.
     */
    private Set<String> biddersAllowingVastUpdate(List<PutObject> vtrackPuts) {
        return vtrackPuts.stream()
                .map(PutObject::getBidder)
                .filter(this::isAllowVastForBidder)
                .collect(Collectors.toSet());
    }

    private boolean isAllowVastForBidder(String bidderName) {
        if (bidderCatalog.isValidName(bidderName)) {
            return bidderCatalog.isModifyingVastXmlAllowed(bidderName);
        } else {
            return allowUnknownBidder && modifyVastForUnknownBidder;
        }
    }

    private void handleCacheResult(AsyncResult<BidCacheResponse> async, RoutingContext routingContext) {
        if (async.failed()) {
            respondWithServerError(routingContext, "Error occurred while sending request to cache", async.cause());
        } else {
            try {
                respondWith(routingContext, HttpResponseStatus.OK, mapper.encode(async.result()));
            } catch (EncodeException e) {
                respondWithServerError(routingContext, "Error occurred while encoding response", e);
            }
        }
    }

    private static void respondWithServerError(RoutingContext routingContext, String message, Throwable exception) {
        logger.error(message, exception);
        respondWith(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                String.format("%s: %s", message, exception.getMessage()));
    }

    private static void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, Endpoint.vtrack,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .setStatusCode(status.code())
                        .end(body));
    }
}
