package org.rtb.vexing.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.model.AmpRequest;
import org.rtb.vexing.auction.model.AmpResponse;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.exception.InvalidRequestException;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtBidRequest;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestPrebid;
import org.rtb.vexing.model.openrtb.ext.request.ExtRequestPrebidCache;
import org.rtb.vexing.model.openrtb.ext.response.ExtBidPrebid;
import org.rtb.vexing.settings.StoredRequestFetcher;
import org.rtb.vexing.validation.RequestValidator;
import org.rtb.vexing.validation.ValidationResult;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AmpHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AmpHandler.class);

    private static final Clock CLOCK = Clock.systemDefaultZone();
    private static final String TAG_ID_REQUEST_PARAM = "tag_id";

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            };

    private final long defaultTimeout;
    private final long defaultStoredRequestsTimeoutMs;
    private final StoredRequestFetcher storedRequestFetcher;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final RequestValidator requestValidator;
    private final ExchangeService exchangeService;
    private final UidsCookieService uidsCookieService;
    private final Metrics metrics;

    public AmpHandler(long defaultTimeout, long defaultStoredRequestsTimeoutMs,
                      StoredRequestFetcher storedRequestFetcher,
                      PreBidRequestContextFactory preBidRequestContextFactory,
                      RequestValidator requestValidator,
                      ExchangeService exchangeService, UidsCookieService uidsCookieService, Metrics metrics) {
        this.defaultTimeout = defaultTimeout;
        this.defaultStoredRequestsTimeoutMs = defaultStoredRequestsTimeoutMs;
        this.storedRequestFetcher = Objects.requireNonNull(storedRequestFetcher);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = CLOCK.millis();

        metrics.incCounter(MetricName.amp_requests);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        final boolean isSafari = isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }

        parseAndValidateAmpRequest(context)
                .compose(ampRequest -> parseAndValidateBidRequest(ampRequest, context, startTime))
                .recover(this::updateErrorRequestsMetric)
                .compose(bidRequest -> {
                    updateAppAndNoCookieMetrics(bidRequest, uidsCookie.hasLiveUids(), isSafari);
                    return exchangeService.holdAuction(bidRequest, uidsCookie, timeout(bidRequest, startTime));
                })
                .map(AmpHandler::toAmpResponse)
                .setHandler(responseResult -> handleResult(responseResult, context));
    }

    private static Future<AmpRequest> parseAndValidateAmpRequest(RoutingContext context) {
        final String tagId = context.request().getParam(TAG_ID_REQUEST_PARAM);
        return StringUtils.isNotBlank(tagId)
                ? Future.succeededFuture(AmpRequest.of(tagId))
                : Future.failedFuture(new InvalidRequestException("AMP requests require an AMP tag_id"));
    }

    private Future<BidRequest> parseAndValidateBidRequest(AmpRequest ampRequest, RoutingContext context,
                                                          long startTime) {
        return toStoredBidRequest(ampRequest, startTime)
                .map(bidRequest -> validateStoredBidRequest(ampRequest.getTagId(), bidRequest))
                .map(bidRequest -> preBidRequestContextFactory.fromRequest(bidRequest, context))
                .map(this::validateBidRequest);
    }

    private Future<BidRequest> toStoredBidRequest(AmpRequest ampRequest, long startTime) {
        final String storedRequestId = ampRequest.getTagId();

        return storedRequestFetcher.getStoredRequestsByAmpId(Collections.singleton(storedRequestId),
                storedRequestFetcherTimeout(startTime))
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed with exception: %s", exception))))
                .compose(storedRequestResult -> storedRequestResult.getErrors().size() > 0
                        ? Future.failedFuture(new InvalidRequestException(storedRequestResult.getErrors()))
                        : Future.succeededFuture(storedRequestResult))
                .map(storedRequestResult -> toBidRequest(storedRequestResult.getStoredIdToJson().get(storedRequestId)));
    }

    private static BidRequest toBidRequest(String bidRequestJson) {
        try {
            return new JsonObject(bidRequestJson).mapTo(BidRequest.class);
        } catch (DecodeException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private static BidRequest validateStoredBidRequest(String storedRequestId, BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            throw new InvalidRequestException(
                    String.format("AMP tag_id '%s' does not include an Imp object. One id required", storedRequestId));
        }

        if (bidRequest.getImp().size() > 1) {
            throw new InvalidRequestException(
                    String.format("AMP tag_id '%s' includes multiple Imp objects. We must have only one",
                            storedRequestId));
        }

        if (bidRequest.getExt() == null) {
            throw new InvalidRequestException("AMP requests require Ext to be set");
        }

        final ExtBidRequest requestExt;
        try {
            requestExt = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }

        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidCache cache = prebid != null ? prebid.getCache() : null;
        if (prebid == null || prebid.getTargeting() == null || cache == null || cache.getBids().isNull()) {
            throw new InvalidRequestException("AMP requests require Targeting and Caching to be set");
        }

        return bidRequest;
    }

    private BidRequest validateBidRequest(BidRequest bidRequest) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            throw new InvalidRequestException(validationResult.getErrors());
        }
        return bidRequest;
    }

    private static AmpResponse toAmpResponse(BidResponse bidResponse) {
        if (bidResponse.getSeatbid() == null) {
            return AmpResponse.of(Collections.emptyMap());
        }

        final Map<String, String> targeting = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .flatMap(bid -> bidTargetingFrom(bid).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return AmpResponse.of(targeting);
    }

    private static Map<String, String> bidTargetingFrom(Bid bid) {
        final ExtPrebid<ExtBidPrebid, ?> extBid;
        try {
            extBid = Json.mapper.convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP targets: %s", e.getMessage()), e);
        }

        if (extBid != null) {
            final ExtBidPrebid extBidPrebid = extBid.getPrebid();

            // Need to extract the targeting parameters from the response, as those are all that
            // go in the AMP response
            final Map<String, String> targeting = extBidPrebid != null ? extBidPrebid.getTargeting() : null;
            if (targeting != null && targeting.keySet().stream()
                    .anyMatch(key -> key != null && key.startsWith("hb_cache_id"))) {
                return targeting;
            }
        }

        return Collections.emptyMap();
    }

    private GlobalTimeout timeout(BidRequest bidRequest, long startTime) {
        final Long tmax = bidRequest.getTmax();
        return GlobalTimeout.create(startTime, tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    private GlobalTimeout storedRequestFetcherTimeout(long startTime) {
        return GlobalTimeout.create(startTime, defaultStoredRequestsTimeoutMs);
    }

    private static void handleResult(AsyncResult<AmpResponse> responseResult, RoutingContext context) {
        if (responseResult.succeeded()) {
            addHeaders(context);
            context.response().end(Json.encode(responseResult.result()));
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                final List<String> messages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", messages);
                context.response()
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end(messages.stream().map(m -> String.format("Invalid request format: %s", m))
                                .collect(Collectors.joining("\n")));
            } else {
                logger.error("Critical error while running the auction", exception);
                context.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end(String.format("Critical error while running the auction: %s", exception.getMessage()));
            }
        }
    }

    private static void addHeaders(RoutingContext context) {
        String originHeader = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            originHeader = ampSourceOrigin.iterator().next();
        }
        if (originHeader == null) {
            // Just to be safe
            originHeader = context.request().headers().get("Origin");
        }

        // Add AMP headers
        context.response()
                .putHeader("AMP-Access-Control-Allow-Source-Origin", originHeader)
                .putHeader("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
    }

    private Future<BidRequest> updateErrorRequestsMetric(Throwable failed) {
        metrics.incCounter(MetricName.error_requests);
        return Future.failedFuture(failed);
    }

    private void updateAppAndNoCookieMetrics(BidRequest bidRequest, boolean isLifeSync, boolean isSafari) {
        if (bidRequest.getApp() != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (isLifeSync) {
            metrics.incCounter(MetricName.amp_no_cookie);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }
    }

    private static boolean isSafari(String userAgent) {
        // this is a simple heuristic based on this article:
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Browser_detection_using_the_user_agent
        //
        // there are libraries available doing different kinds of User-Agent analysis but they impose performance
        // implications as well, example: https://github.com/nielsbasjes/yauaa
        return StringUtils.isNotBlank(userAgent) && userAgent.contains("AppleWebKit") && userAgent.contains("Safari")
                && !userAgent.contains("Chrome") && !userAgent.contains("Chromium");
    }
}
