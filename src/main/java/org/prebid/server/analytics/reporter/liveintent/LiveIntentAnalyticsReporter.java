package org.prebid.server.analytics.reporter.liveintent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LiveIntentAnalyticsReporter implements AnalyticsReporter {

    private final HttpClient httpClient;
    private final LiveIntentAnalyticsProperties properties;
    private final JacksonMapper jacksonMapper;

    private static final Logger logger = LoggerFactory.getLogger(LiveIntentAnalyticsReporter.class);

    public LiveIntentAnalyticsReporter(
            LiveIntentAnalyticsProperties properties,
            HttpClient httpClient,
            JacksonMapper jacksonMapper
    ) {
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

    private Future<Void> processNotificationEvent(NotificationEvent notificationEvent) {
        final String url = properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-winning-bid?"
                + "b=" + notificationEvent.getBidder()
                + "&bidId=" + notificationEvent.getBidId();
        return httpClient.get(url, properties.getTimeoutMs()).mapEmpty();
    }

    private Future<Void> processAuctionEvent(AuctionContext auctionContext) {
        try {
            final BidRequest bidRequest = Optional.ofNullable(auctionContext.getBidRequest())
                    .orElseThrow(() -> new PreBidException("Bid request should not be empty"));
            final BidResponse bidResponse = Optional.ofNullable(auctionContext.getBidResponse())
                    .orElseThrow(() -> new PreBidException("Bid response should not be empty"));
            final Optional<ExtRequestPrebid> requestPrebid = Optional.ofNullable(bidRequest.getExt())
                    .flatMap(ext -> Optional.of(ext.getPrebid()));

            final List<Activity> activities = getActivities(auctionContext);

            final List<PbsjBid> pbsjBids = bidResponse.getSeatbid().stream()
                    .flatMap(seatBid -> seatBid.getBid().stream())
                    .flatMap(bid ->
                        bidRequest.getImp().stream()
                            .filter(impItem -> impItem.getId().equals(bid.getImpid()))
                            .map(imp ->
                                PbsjBid.builder()
                                    .bidId(bid.getId())
                                    .price(bid.getPrice())
                                    .adUnitId(imp.getTagid())
                                    .enriched(isEnriched(activities))
                                    .currency(bidResponse.getCur())
                                    .treatmentRate(getTreatmentRate(activities))
                                    .timestamp(requestPrebid.map(ExtRequestPrebid::getAuctiontimestamp).orElse(0L))
                                    .partnerId(properties.getPartnerId())
                                    .build()
                            )
            ).toList();

            return httpClient.post(
                    properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-bids",
                    jacksonMapper.encodeToString(pbsjBids),
                    properties.getTimeoutMs()
                 ).mapEmpty();
        } catch (Exception e) {
            logger.error("Error processing event: {}", e.getMessage());
            return Future.failedFuture(e);
        }
    }

    private List<Activity> getActivities(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getHookExecutionContext)
                .map(HookExecutionContext::getStageOutcomes)
                .map(stages -> stages.get(Stage.processed_auction_request)).stream()
                .flatMap(Collection::stream)
                .filter(stageExecutionOutcome -> "auction-request".equals(stageExecutionOutcome.getEntity()))
                .map(StageExecutionOutcome::getGroups)
                .flatMap(Collection::stream)
                .map(GroupExecutionOutcome::getHooks)
                .flatMap(Collection::stream)
                .filter(hook ->
                        "liveintent-omni-channel-identity-enrichment-hook".equals(hook.getHookId().getModuleCode())
                                && hook.getStatus() == ExecutionStatus.success
                )
                .map(HookExecutionOutcome::getAnalyticsTags)
                .map(Tags::activities)
                .flatMap(Collection::stream)
                .toList();
    }

    private Float getTreatmentRate(List<Activity> activities) {
        return activities
                .stream()
                .filter(activity -> "liveintent-treatment-rate".equals(activity.name()))
                .findFirst()
                .map(activity -> {
                    try {
                        return Float.parseFloat(activity.status());
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid treatment rate value: {}", activity.status());
                        throw e;
                    }
                })
                .orElse(null);
    }

    private boolean isEnriched(List<Activity> activities) {
        return activities.stream().anyMatch(activity -> "liveintent-enriched".equals(activity.name()));
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
