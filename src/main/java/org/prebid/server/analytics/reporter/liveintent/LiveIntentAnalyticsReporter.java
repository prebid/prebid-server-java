package org.prebid.server.analytics.reporter.liveintent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.reporter.liveintent.model.LiveIntentAnalyticsProperties;
import org.prebid.server.analytics.reporter.liveintent.model.PbsjBid;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LiveIntentAnalyticsReporter implements AnalyticsReporter {

    private static final Logger logger = LoggerFactory.getLogger(LiveIntentAnalyticsReporter.class);

    private static final String LIVEINTENT_HOOK_ID = "liveintent-omni-channel-identity-enrichment-hook";

    private final HttpClient httpClient;
    private final LiveIntentAnalyticsProperties properties;
    private final JacksonMapper jacksonMapper;

    public LiveIntentAnalyticsReporter(
            LiveIntentAnalyticsProperties properties,
            HttpClient httpClient,
            JacksonMapper jacksonMapper) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.properties = Objects.requireNonNull(properties);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
        if (event instanceof AuctionEvent auctionEvent) {
            return processAuctionEvent(auctionEvent.getAuctionContext());
        } else if (event instanceof NotificationEvent notificationEvent) {
            return processNotificationEvent(notificationEvent);
        }

        return Future.succeededFuture();
    }

    private Future<Void> processAuctionEvent(AuctionContext auctionContext) {
        if (auctionContext.getBidRequest() == null) {
            return Future.failedFuture(new PreBidException("Bid request should not be empty"));
        }

        if (auctionContext.getBidResponse() == null) {
            return Future.succeededFuture();
        }

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final BidResponse bidResponse = auctionContext.getBidResponse();

        final Optional<Activity> activity = getActivities(auctionContext);
        final boolean isEnriched = isEnriched(activity);
        final Float treatmentRate = getTreatmentRate(activity);
        final Long timestamp = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .orElse(0L);

        final List<PbsjBid> pbsjBids = CollectionUtils.emptyIfNull(bidResponse.getSeatbid()).stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> buildPbsjBid(bidRequest, bidResponse, bid, isEnriched, treatmentRate, timestamp))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        try {
            return httpClient.post(
                    new URIBuilder(properties.getAnalyticsEndpoint())
                            .setPath("/analytic-events/pbsj-bids")
                            .build()
                            .toString(),
                    jacksonMapper.encodeToString(pbsjBids),
                    properties.getTimeoutMs())
                    .mapEmpty();
        } catch (Exception e) {
            logger.error("Error processing event: {}", e.getMessage());
            return Future.failedFuture(e);
        }
    }

    private Optional<Activity> getActivities(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getHookExecutionContext)
                .map(HookExecutionContext::getStageOutcomes)
                .map(stages -> stages.get(Stage.processed_auction_request))
                .stream()
                .flatMap(Collection::stream)
                .filter(stageExecutionOutcome -> "auction-request".equals(stageExecutionOutcome.getEntity()))
                .map(StageExecutionOutcome::getGroups)
                .flatMap(Collection::stream)
                .map(GroupExecutionOutcome::getHooks)
                .flatMap(Collection::stream)
                .filter(hook ->
                        LIVEINTENT_HOOK_ID.equals(hook.getHookId().getModuleCode())
                                && hook.getStatus() == ExecutionStatus.success)
                .map(HookExecutionOutcome::getAnalyticsTags)
                .filter(Objects::nonNull)
                .map(Tags::activities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private boolean isEnriched(Optional<Activity> activity) {
        return activity.stream().anyMatch(a -> "liveintent-enriched".equals(a.name()));
    }

    private Float getTreatmentRate(Optional<Activity> activity) {
        return activity.stream()
                .flatMap(a -> a.results().stream())
                .filter(a -> a.values().has("treatmentRate"))
                .findFirst()
                .map(a -> a.values().at("treatmentRate").floatValue())
                .orElse(null);
    }

    private Optional<PbsjBid> buildPbsjBid(
            BidRequest bidRequest,
            BidResponse bidResponse,
            Bid bid,
            boolean isEnriched,
            Float treatmentRate,
            Long timestamp) {

        return bidRequest.getImp().stream()
                .filter(impItem -> impItem.getId().equals(bid.getImpid()))
                .map(imp -> PbsjBid.builder()
                                    .bidId(bid.getId())
                                    .price(bid.getPrice())
                                    .adUnitId(imp.getTagid())
                                    .enriched(isEnriched)
                                    .currency(bidResponse.getCur())
                                    .treatmentRate(treatmentRate)
                                    .timestamp(timestamp)
                                    .partnerId(properties.getPartnerId())
                                    .build())
                .findFirst();
    }

    private Future<Void> processNotificationEvent(NotificationEvent notificationEvent) {
        try {
            final String url = new URIBuilder(properties.getAnalyticsEndpoint())
                    .setPath("/analytic-events/pbsj-winning-bid")
                    .setParameter("b", notificationEvent.getBidder())
                    .setParameter("bidId", notificationEvent.getBidId())
                    .build()
                    .toString();
            return httpClient.get(url, properties.getTimeoutMs()).mapEmpty();
        } catch (URISyntaxException e) {
            logger.error("Error composing url for notification event: {}", e.getMessage());
            return Future.failedFuture(e);
        }
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "liveintentAnalytics";
    }
}
