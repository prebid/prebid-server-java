package org.prebid.server.deals;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.deals.events.ApplicationEventProcessor;
import org.prebid.server.deals.lineitem.DeliveryPlan;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.lineitem.LineItemStatus;
import org.prebid.server.deals.model.DeliveryProgressProperties;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.deals.proto.report.DeliverySchedule;
import org.prebid.server.deals.proto.report.LineItemStatusReport;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.CriteriaLogManager;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tracks {@link LineItem}s' progress.
 */
public class DeliveryProgressService implements ApplicationEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProgressService.class);

    private final DeliveryProgressProperties deliveryProgressProperties;
    private final LineItemService lineItemService;
    private final DeliveryStatsService deliveryStatsService;
    private final DeliveryProgressReportFactory deliveryProgressReportFactory;
    private final Clock clock;
    private final CriteriaLogManager criteriaLogManager;

    private final long lineItemStatusTtl;

    protected DeliveryProgress overallDeliveryProgress;
    protected DeliveryProgress currentDeliveryProgress;

    public DeliveryProgressService(DeliveryProgressProperties deliveryProgressProperties,
                                   LineItemService lineItemService,
                                   DeliveryStatsService deliveryStatsService,
                                   DeliveryProgressReportFactory deliveryProgressReportFactory,
                                   Clock clock,
                                   CriteriaLogManager criteriaLogManager) {
        this.deliveryProgressProperties = Objects.requireNonNull(deliveryProgressProperties);
        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.deliveryStatsService = Objects.requireNonNull(deliveryStatsService);
        this.deliveryProgressReportFactory = Objects.requireNonNull(deliveryProgressReportFactory);
        this.clock = Objects.requireNonNull(clock);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);

        this.lineItemStatusTtl = TimeUnit.SECONDS.toMillis(deliveryProgressProperties.getLineItemStatusTtlSeconds());

        final ZonedDateTime now = ZonedDateTime.now(clock);
        overallDeliveryProgress = DeliveryProgress.of(now, lineItemService);
        currentDeliveryProgress = DeliveryProgress.of(now, lineItemService);
    }

    public void shutdown() {
        createDeliveryProgressReports(ZonedDateTime.now(clock));
        deliveryStatsService.sendDeliveryProgressReports();
    }

    /**
     * Updates copy of overall {@link DeliveryProgress} with current delivery progress and
     * creates {@link DeliveryProgressReport}.
     */
    public DeliveryProgressReport getOverallDeliveryProgressReport() {
        final DeliveryProgress overallDeliveryProgressCopy =
                overallDeliveryProgress.copyWithOriginalPlans();

        lineItemService.getLineItems()
                .forEach(lineItem -> overallDeliveryProgressCopy.getLineItemStatuses()
                        .putIfAbsent(lineItem.getLineItemId(), LineItemStatus.of(lineItem.getLineItemId(),
                                lineItem.getSource(), lineItem.getDealId(), lineItem.getExtLineItemId(),
                                lineItem.getAccountId())));

        overallDeliveryProgressCopy.mergeFrom(currentDeliveryProgress);
        return deliveryProgressReportFactory.fromDeliveryProgress(overallDeliveryProgressCopy, ZonedDateTime.now(clock),
                true);
    }

    /**
     * Updates delivery progress from {@link AuctionContext} statistics.
     */
    @Override
    public void processAuctionEvent(AuctionContext auctionContext) {
        processAuctionEvent(auctionContext.getTxnLog(), auctionContext.getAccount().getId(), ZonedDateTime.now(clock));
    }

    /**
     * Updates delivery progress from {@link AuctionContext} statistics for defined date.
     */
    protected void processAuctionEvent(TxnLog txnLog, String accountId, ZonedDateTime now) {
        final Map<String, Integer> planIdToTokenPriority = new HashMap<>();

        txnLog.lineItemSentToClientAsTopMatch().stream()
                .map(lineItemService::getLineItemById)
                .filter(Objects::nonNull)
                .filter(lineItem -> lineItem.getActiveDeliveryPlan() != null)
                .forEach(lineItem -> incrementTokens(lineItem, now, planIdToTokenPriority));

        currentDeliveryProgress.recordTransactionLog(txnLog, planIdToTokenPriority, accountId);
    }

    /**
     * Updates delivery progress with win event.
     */
    @Override
    public void processLineItemWinEvent(String lineItemId) {
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        if (lineItem != null) {
            currentDeliveryProgress.recordWinEvent(lineItemId);
            criteriaLogManager.log(logger, lineItem.getAccountId(), lineItem.getSource(), lineItemId,
                    String.format("Win event for LineItem with id %s was recorded", lineItemId), logger::debug);
        }
    }

    @Override
    public void processDeliveryProgressUpdateEvent() {
        lineItemService.getLineItems()
                .stream()
                .filter(lineItem -> lineItem.getActiveDeliveryPlan() != null)
                .forEach(this::mergePlanFromLineItem);
    }

    private void mergePlanFromLineItem(LineItem lineItem) {
        overallDeliveryProgress.upsertPlanReferenceFromLineItem(lineItem);
        currentDeliveryProgress.mergePlanFromLineItem(lineItem);
    }

    /**
     * Prepare report from statuses to send it to delivery stats.
     */
    public void createDeliveryProgressReports(ZonedDateTime now) {
        final DeliveryProgress deliveryProgressToReport = currentDeliveryProgress;

        currentDeliveryProgress = DeliveryProgress.of(now, lineItemService);

        deliveryProgressToReport.setEndTimeStamp(now);
        deliveryProgressToReport.updateWithActiveLineItems(lineItemService.getLineItems());

        overallDeliveryProgress.mergeFrom(deliveryProgressToReport);

        deliveryStatsService.addDeliveryProgress(deliveryProgressToReport,
                overallDeliveryProgress.getLineItemStatuses());

        overallDeliveryProgress.cleanLineItemStatuses(
                now, lineItemStatusTtl, deliveryProgressProperties.getCachedPlansNumber());
    }

    public void invalidateLineItemsByIds(List<String> lineItemIds) {
        overallDeliveryProgress.getLineItemStatuses().entrySet()
                .removeIf(stringLineItemEntry -> lineItemIds.contains(stringLineItemEntry.getKey()));
        currentDeliveryProgress.getLineItemStatuses().entrySet()
                .removeIf(stringLineItemEntry -> lineItemIds.contains(stringLineItemEntry.getKey()));
    }

    public void invalidateLineItems() {
        overallDeliveryProgress.getLineItemStatuses().clear();
        currentDeliveryProgress.getLineItemStatuses().clear();
    }

    /**
     * Increments tokens for specified in parameters lineItem, plan and class priority.
     */
    protected void incrementTokens(LineItem lineItem, ZonedDateTime now, Map<String, Integer> planIdToTokenPriority) {
        final Integer classPriority = lineItem.incSpentToken(now);
        if (classPriority != null) {
            planIdToTokenPriority.put(lineItem.getActiveDeliveryPlan().getPlanId(), classPriority);
        }
    }

    /**
     * Returns {@link LineItemStatusReport} for the given {@link LineItem}'s ID.
     */
    public LineItemStatusReport getLineItemStatusReport(String lineItemId, ZonedDateTime now) {
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        if (lineItem == null) {
            throw new PreBidException(String.format("LineItem not found: %s", lineItemId));
        }

        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();
        if (activeDeliveryPlan == null) {
            return LineItemStatusReport.builder()
                    .lineItemId(lineItemId)
                    .build();
        }

        final DeliverySchedule deliverySchedule = DeliveryProgressReportFactory.toDeliverySchedule(activeDeliveryPlan);
        return LineItemStatusReport.builder()
                .lineItemId(lineItemId)
                .deliverySchedule(deliverySchedule)
                .readyToServeTimestamp(lineItem.getReadyAt())
                .spentTokens(activeDeliveryPlan.getSpentTokens())
                .pacingFrequency(activeDeliveryPlan.getDeliveryRateInMilliseconds())
                .build();
    }
}
