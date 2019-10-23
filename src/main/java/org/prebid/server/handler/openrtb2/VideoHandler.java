package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.auction.AmpResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoRequestFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.response.AmpResponse;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/*
1. Parse "storedrequestid" field from simplified endpoint request body.
2. If config flag to require that field is set (which it will be for us) and this field is not given then error out here.
3. Load the stored request JSON for the given storedrequestid, if the id was invalid then error out here.
4. Use "json-patch" 3rd party library to merge the request body JSON data into the stored request JSON data.
5. Unmarshal the merged JSON data into a Go structure.
6. Add fields from merged JSON data that correspond to an OpenRTB request into the OpenRTB bid request we are building.
	a. Unmarshal certain OpenRTB defined structs directly into the OpenRTB bid request.
	b. In cases where customized logic is needed just copy/fill the fields in directly.
7. Call setFieldsImplicitly from auction.go to get basic data from the HTTP request into an OpenRTB bid request to start building the OpenRTB bid request.
8. Loop through ad pods to build array of Imps into OpenRTB request, for each pod:
	a. Load the stored impression to use as the basis for impressions generated for this pod from the configid field.
	b. NumImps = adpoddurationsec / MIN_VALUE(allowedDurations)
	c. Build impression array for this pod:
		I.Create array of NumImps entries initialized to the base impression loaded from the configid.
			1. If requireexactdurations = true, iterate over allowdDurations and for (NumImps / len(allowedDurations)) number of Imps set minduration = maxduration = allowedDurations[i]
			2. If requireexactdurations = false, set maxduration = MAX_VALUE(allowedDurations)
		II. Set Imp.id field to "podX_Y" where X is the pod index and Y is the impression index within this pod.
	d. Append impressions for this pod to the overall list of impressions in the OpenRTB bid request.
9. Call validateRequest() function from auction.go to validate the generated request.
10. Call HoldAuction() function to run the auction for the OpenRTB bid request that was built in the previous step.
11. Build proper response format.
*/

public class VideoHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VideoHandler.class);

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };
    private static final TypeReference<ExtBidResponse> EXT_BID_RESPONSE_TYPE_REFERENCE =
            new TypeReference<ExtBidResponse>() {
            };
    private static final MetricName REQUEST_TYPE_METRIC = MetricName.amp;

    private final VideoRequestFactory videoRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final BidderCatalog bidderCatalog;
    private final Set<String> biddersSupportingCustomTargeting;
    private final AmpResponsePostProcessor ampResponsePostProcessor;

    public VideoHandler(VideoRequestFactory videoRequestFactory, ExchangeService exchangeService,
                        AnalyticsReporter analyticsReporter, Metrics metrics, Clock clock, BidderCatalog bidderCatalog,
                        Set<String> biddersSupportingCustomTargeting, AmpResponsePostProcessor ampResponsePostProcessor,
                        boolean isVideoStoredIdRequired) {
        this.videoRequestFactory = Objects.requireNonNull(videoRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.biddersSupportingCustomTargeting = Objects.requireNonNull(biddersSupportingCustomTargeting);
        this.ampResponsePostProcessor = Objects.requireNonNull(ampResponsePostProcessor);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(routingContext.request().headers().get(HttpUtil.USER_AGENT_HEADER));
        metrics.updateSafariRequestsMetric(isSafari);








// Should be changable
        videoRequestFactory.fromRequest(routingContext, startTime)
                .map(context -> context.toBuilder()
                        .requestTypeMetric(REQUEST_TYPE_METRIC)
                        .build())

                .map(context -> addToEvent(context, ampEventBuilder::auctionContext, context))
                .map(context -> updateAppAndNoCookieAndImpsMetrics(context, isSafari))

                .compose(context -> exchangeService.holdAuction(context)
                        .map(bidResponse -> Tuple2.of(bidResponse, context)))

                .map(result -> addToEvent(result.getLeft(), ampEventBuilder::bidResponse, result))
                .map(result -> Tuple3.of(result.getLeft(), result.getRight(),
                        toAmpResponse(result.getRight().getBidRequest(), result.getLeft())))

                .compose(result -> ampResponsePostProcessor.postProcess(result.getMiddle().getBidRequest(),
                        result.getLeft(), result.getRight(), routingContext))

                .map(ampResponse -> addToEvent(ampResponse.getTargeting(), ampEventBuilder::targeting, ampResponse))
                .setHandler(responseResult -> handleResult(responseResult, ampEventBuilder, routingContext, startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsMetrics(AuctionContext context, boolean isSafari) {
        final BidRequest bidRequest = context.getBidRequest();
        final UidsCookie uidsCookie = context.getUidsCookie();

        final List<Imp> imps = bidRequest.getImp();
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                isSafari, imps.size());

        metrics.updateImpTypesMetrics(imps);

        return context;
    }

    private AmpResponse toAmpResponse(BidRequest bidRequest, BidResponse bidResponse) {
        // Fetch targeting information from response bids
        final List<SeatBid> seatBids = bidResponse.getSeatbid();

        final Map<String, JsonNode> targeting = seatBids == null ? Collections.emptyMap() : seatBids.stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .flatMap(bid -> targetingFrom(bid, seatBid.getSeat()).entrySet().stream()))
                .map(entry -> Tuple2.of(entry.getKey(), TextNode.valueOf(entry.getValue())))
                .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

        final ExtResponseDebug extResponseDebug;
        final Map<String, List<ExtBidderError>> errors;
        // Fetch debug and errors information from response if requested
        if (isDebugEnabled(bidRequest)) {
            final ExtBidResponse extBidResponse = extResponseFrom(bidResponse);

            extResponseDebug = extResponseDebugFrom(extBidResponse);
            errors = errorsFrom(extBidResponse);
        } else {
            extResponseDebug = null;
            errors = null;
        }

        return AmpResponse.of(targeting, extResponseDebug, errors);
    }

    private Map<String, String> targetingFrom(Bid bid, String bidder) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extBid;
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

                return enrichWithCustomTargeting(targeting, extBid, bidder);
            }
        }

        return Collections.emptyMap();
    }

    private Map<String, String> enrichWithCustomTargeting(
            Map<String, String> targeting, ExtPrebid<ExtBidPrebid, ObjectNode> extBid, String bidder) {

        final Map<String, String> customTargeting = customTargetingFrom(extBid.getBidder(), bidder);
        if (!customTargeting.isEmpty()) {
            final Map<String, String> enrichedTargeting = new HashMap<>(targeting);
            enrichedTargeting.putAll(customTargeting);
            return enrichedTargeting;
        }
        return targeting;
    }

    private Map<String, String> customTargetingFrom(ObjectNode extBidBidder, String bidder) {
        if (extBidBidder != null && biddersSupportingCustomTargeting.contains(bidder)
                && bidderCatalog.isValidName(bidder)) {

            return bidderCatalog.bidderByName(bidder).extractTargeting(extBidBidder);
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Determines debug flag from {@link BidRequest}.
     */
    private static boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtBidRequest extBidRequest = extBidRequestFrom(bidRequest);
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    /**
     * Extracts {@link ExtBidRequest} from {@link BidRequest}.
     */
    private static ExtBidRequest extBidRequestFrom(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class)
                    : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }
    }

    private static ExtBidResponse extResponseFrom(BidResponse bidResponse) {
        try {
            return Json.mapper.convertValue(bidResponse.getExt(), EXT_BID_RESPONSE_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Critical error while unpacking AMP bid response: %s", e.getMessage()), e);
        }
    }

    private static ExtResponseDebug extResponseDebugFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getDebug() : null;
    }

    private static Map<String, List<ExtBidderError>> errorsFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getErrors() : null;
    }

    private void handleResult(AsyncResult<AmpResponse> responseResult, AmpEvent.AmpEventBuilder ampEventBuilder,
                              RoutingContext context, long startTime) {
        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        final String origin = originFrom(context);
        ampEventBuilder.origin(origin);

        // Add AMP headers
        context.response().headers()
                .add("AMP-Access-Control-Allow-Source-Origin", origin)
                .add("Access-Control-Expose-Headers", "AMP-Access-Control-Allow-Source-Origin");

        if (responseResult.succeeded()) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK.code();
            context.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = Json.encode(responseResult.result());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;
                errorMessages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", errorMessages);

                status = HttpResponseStatus.BAD_REQUEST.code();
                body = errorMessages.stream().map(
                        msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.joining("\n"));
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String errorMessage = exception.getMessage();
                logger.info("Unauthorized: {0}", errorMessage);

                errorMessages = Collections.singletonList(errorMessage);

                status = HttpResponseStatus.UNAUTHORIZED.code();
                body = String.format("Unauthorised: %s", errorMessage);
            } else {
                final String message = exception.getMessage();

                metricRequestStatus = MetricName.err;
                errorMessages = Collections.singletonList(message);
                logger.error("Critical error while running the auction", exception);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final AmpEvent ampEvent = ampEventBuilder.status(status).errors(errorMessages).build();
        respondWith(context, status, body, startTime, metricRequestStatus, ampEvent);
    }

    private static String originFrom(RoutingContext context) {
        String origin = null;
        final List<String> ampSourceOrigin = context.queryParam("__amp_source_origin");
        if (CollectionUtils.isNotEmpty(ampSourceOrigin)) {
            origin = ampSourceOrigin.get(0);
        }
        if (origin == null) {
            // Just to be safe
            origin = ObjectUtils.defaultIfNull(context.request().headers().get("Origin"), StringUtils.EMPTY);
        }
        return origin;
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime,
                             MetricName metricRequestStatus, AmpEvent event) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
        } else {
            context.response()
                    .exceptionHandler(this::handleResponseException)
                    .setStatusCode(status)
                    .end(body);

            metrics.updateRequestTimeMetric(clock.millis() - startTime);
            metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, metricRequestStatus);
            analyticsReporter.processEvent(event);
        }
    }

    private void handleResponseException(Throwable throwable) {
        logger.warn("Failed to send amp response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}
