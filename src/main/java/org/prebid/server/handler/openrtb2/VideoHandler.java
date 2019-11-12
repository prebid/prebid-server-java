package org.prebid.server.handler.openrtb2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.video.PodError;
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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.VideoRequestFactory;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseVideoTargeting;
import org.prebid.server.proto.response.VideoResponse;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
1. Parse "storedrequestid" field from simplified endpoint request body.
2. If config flag to require that field is set (which it will be for us) and this field is not given then error
 out here.
3. Load the stored request JSON for the given storedrequestid, if the id was invalid then error out here.
4. Use "json-patch" 3rd party library to merge the request body JSON data into the stored request JSON data.
5. Unmarshal the merged JSON data into a Go structure.
6. Add fields from merged JSON data that correspond to an OpenRTB request into the OpenRTB bid request we are building.
    a. Unmarshal certain OpenRTB defined structs directly into the OpenRTB bid request.
    b. In cases where customized logic is needed just copy/fill the fields in directly.
7. Call setFieldsImplicitly from auction.go to get basic data from the HTTP request into an OpenRTB bid request to
start building the OpenRTB bid request.
8. Loop through ad pods to build array of Imps into OpenRTB request, for each pod:
    a. Load the stored impression to use as the basis for impressions generated for this pod from
    the configid field.
   b. NumImps = adpoddurationsec / MIN_VALUE(allowedDurations)
   c. Build impression array for this pod:
      I.Create array of NumImps entries initialized to the base impression loaded from the
      configid.
         1. If requireexactdurations = true, iterate over allowdDurations and for (NumImps / len(allowedDurations))
         number of Imps set minduration = maxduration = allowedDurations[i]
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

    private static final MetricName REQUEST_TYPE_METRIC = MetricName.video;

    private final VideoRequestFactory videoRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;

    public VideoHandler(VideoRequestFactory videoRequestFactory, ExchangeService exchangeService,
                        AnalyticsReporter analyticsReporter, Metrics metrics, Clock clock) {
        this.videoRequestFactory = Objects.requireNonNull(videoRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
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
        final VideoEvent.VideoEventBuilder videoEventBuilder = VideoEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        videoRequestFactory.fromRequest(routingContext, startTime)
                .map(contextToErrors -> doAndTupleRight(context -> context.toBuilder()
                                .requestTypeMetric(REQUEST_TYPE_METRIC)
                                .build(),
                        contextToErrors))
                .map(contextToErrors -> doAndTupleRight(
                        context -> addToEvent(context, videoEventBuilder::auctionContext, context),
                        contextToErrors))

                .compose(contextToErrors -> exchangeService.holdAuction(contextToErrors.getLeft())
                        .map(bidResponse -> Tuple2.of(
                                Tuple2.of(bidResponse, contextToErrors.getLeft()),
                                contextToErrors.getRight())))

                .map(result -> toVideoResponse(result.getLeft().getRight().getBidRequest(), result.getLeft().getLeft(),
                        result.getRight()))

                .map(videoResponse -> addToEvent(videoResponse, videoEventBuilder::bidResponse, videoResponse))
                .setHandler(responseResult -> handleResult(responseResult, videoEventBuilder, routingContext,
                        startTime));
    }

    private static <T, R, E> Tuple2<R, E> doAndTupleRight(Function<T, R> consumer, Tuple2<T, E> tuple2) {
        return Tuple2.of(consumer.apply(tuple2.getLeft()), tuple2.getRight());
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private VideoResponse toVideoResponse(BidRequest bidRequest, BidResponse bidResponse, List<PodError> podErrors) {
        final List<Bid> bids = bids(bidResponse);
        final boolean anyBidsReturned = CollectionUtils.isNotEmpty(bids);
        final List<ExtAdPod> adPods = bidsToAdPodWithTargeting(bids);

        if (anyBidsReturned && CollectionUtils.isEmpty(adPods)) {
            throw new PreBidException("caching failed for all bids");
        }

        adPods.addAll(transformToAdPod(podErrors));

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
        return VideoResponse.of(adPods, extResponseDebug, errors, null);
    }

    private static List<Bid> bids(BidResponse bidResponse) {
        if (bidResponse != null && CollectionUtils.isNotEmpty(bidResponse.getSeatbid())) {
            return bidResponse.getSeatbid().stream()
                    .filter(Objects::nonNull)
                    .map(SeatBid::getBid)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private List<ExtAdPod> bidsToAdPodWithTargeting(List<Bid> bids) {
        final List<ExtAdPod> adPods = new ArrayList<>();
        for (Bid bid : bids) {
            final Map<String, String> targeting = targeting(bid);
            if (targeting.get("hb_uuid") == null) {
                continue;
            }
            final String impId = bid.getImpid();
            final Integer podId = Integer.parseInt(impId.split("_")[0]);

            final ExtResponseVideoTargeting videoTargeting = ExtResponseVideoTargeting.of(
                    targeting.get("hb_pb"),
                    targeting.get("hb_pb_cat_dur"),
                    targeting.get("hb_uuid"));

            ExtAdPod adPod = adPods.stream()
                    .filter(extAdPod -> extAdPod.getPodid().equals(podId))
                    .findFirst()
                    .orElse(null);

            if (adPod == null) {
                adPod = ExtAdPod.of(podId, new ArrayList<>(), null);
                adPods.add(adPod);
            }
            adPod.getTargeting().add(videoTargeting);
        }
        return adPods;
    }

    private Map<String, String> targeting(Bid bid) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extBid;
        try {
            extBid = Json.mapper.convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            return Collections.emptyMap();
        }

        final ExtBidPrebid extBidPrebid = extBid != null ? extBid.getPrebid() : null;
        final Map<String, String> targeting = extBidPrebid != null ? extBidPrebid.getTargeting() : null;
        return targeting != null ? targeting : Collections.emptyMap();
    }

    private static List<ExtAdPod> transformToAdPod(List<PodError> podErrors) {
        return podErrors.stream()
                .map(podError -> ExtAdPod.of(podError.getPodId(), null, podError.getPodErrors()))
                .collect(Collectors.toList());
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
                    String.format("Critical error while unpacking Video bid response: %s", e.getMessage()), e);
        }
    }

    private static ExtResponseDebug extResponseDebugFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getDebug() : null;
    }

    private static Map<String, List<ExtBidderError>> errorsFrom(ExtBidResponse extBidResponse) {
        return extBidResponse != null ? extBidResponse.getErrors() : null;
    }

    private void handleResult(AsyncResult<VideoResponse> responseResult, VideoEvent.VideoEventBuilder videoEventBuilder,
                              RoutingContext context, long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();
        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        if (responseSucceeded) {
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
                body = errorMessages.stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.joining("\n"));
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String errorMessage = exception.getMessage();
                logger.info("Unauthorized: {0}", errorMessage);

                errorMessages = Collections.singletonList(errorMessage);

                status = HttpResponseStatus.UNAUTHORIZED.code();
                body = String.format("Unauthorised: %s", errorMessage);
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }
        final VideoEvent auctionEvent = videoEventBuilder.status(status).errors(errorMessages).build();
        respondWith(context, status, body, startTime, metricRequestStatus, auctionEvent);
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime,
                             MetricName metricRequestStatus, VideoEvent event) {
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
        logger.warn("Failed to send video response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
    }
}
