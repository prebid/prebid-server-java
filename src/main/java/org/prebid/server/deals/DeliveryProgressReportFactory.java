package org.prebid.server.deals;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.deals.lineitem.DeliveryPlan;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.lineitem.DeliveryToken;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.deals.proto.report.DeliveryProgressReportBatch;
import org.prebid.server.deals.proto.report.DeliverySchedule;
import org.prebid.server.deals.proto.report.LineItemStatus;
import org.prebid.server.deals.proto.report.LostToLineItem;
import org.prebid.server.deals.proto.report.Token;
import org.prebid.server.util.ObjectUtil;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DeliveryProgressReportFactory {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProgressReportFactory.class);

    private static final LostToLineItemComparator LOST_TO_LINE_ITEM_COMPARATOR = new LostToLineItemComparator();

    private final DeploymentProperties deploymentProperties;
    private final int competitorsNumber;
    private final LineItemService lineItemService;
    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    public DeliveryProgressReportFactory(
            DeploymentProperties deploymentProperties, int competitorsNumber, LineItemService lineItemService) {
        this.deploymentProperties = Objects.requireNonNull(deploymentProperties);
        this.competitorsNumber = competitorsNumber;
        this.lineItemService = Objects.requireNonNull(lineItemService);
    }

    public DeliveryProgressReport fromDeliveryProgress(
            DeliveryProgress deliveryProgress,
            ZonedDateTime now,
            boolean isOverall) {
        final List<org.prebid.server.deals.lineitem.LineItemStatus> lineItemStatuses =
                new ArrayList<>(deliveryProgress.getLineItemStatuses().values());
        return DeliveryProgressReport.builder()
                .reportId(UUID.randomUUID().toString())
                .reportTimeStamp(now != null ? formatTimeStamp(now) : null)
                .dataWindowStartTimeStamp(isOverall ? null : formatTimeStamp(deliveryProgress.getStartTimeStamp()))
                .dataWindowEndTimeStamp(isOverall ? null : formatTimeStamp(deliveryProgress.getEndTimeStamp()))
                .instanceId(deploymentProperties.getPbsHostId())
                .region(deploymentProperties.getPbsRegion())
                .vendor(deploymentProperties.getPbsVendor())
                .clientAuctions(deliveryProgress.getRequests().sum())
                .lineItemStatus(makeLineItemStatusReports(deliveryProgress, lineItemStatuses,
                        deliveryProgress.getLineItemStatuses(), isOverall))
                .build();
    }

    public DeliveryProgressReportBatch batchFromDeliveryProgress(
            DeliveryProgress deliveryProgress,
            Map<String, org.prebid.server.deals.lineitem.LineItemStatus> overallLineItemStatuses,
            ZonedDateTime now,
            int batchSize,
            boolean isOverall) {
        final List<org.prebid.server.deals.lineitem.LineItemStatus> lineItemStatuses
                = new ArrayList<>(deliveryProgress.getLineItemStatuses().values());
        final String reportId = UUID.randomUUID().toString();
        final String reportTimeStamp = now != null ? formatTimeStamp(now) : null;
        final String dataWindowStartTimeStamp = isOverall
                ? null
                : formatTimeStamp(deliveryProgress.getStartTimeStamp());
        final String dataWindowEndTimeStamp = isOverall ? null : formatTimeStamp(deliveryProgress.getEndTimeStamp());
        final long clientAuctions = deliveryProgress.getRequests().sum();

        final int lineItemsCount = lineItemStatuses.size();
        final int batchesNumber = lineItemsCount / batchSize + (lineItemsCount % batchSize > 0 ? 1 : 0);
        final Set<DeliveryProgressReport> reportsBatch = IntStream.range(0, batchesNumber)
                .mapToObj(batchNumber -> updateReportWithLineItems(deliveryProgress, lineItemStatuses,
                        overallLineItemStatuses, lineItemsCount, batchNumber, batchSize, isOverall))
                .map(deliveryProgressReport -> deliveryProgressReport
                        .reportId(reportId)
                        .reportTimeStamp(reportTimeStamp)
                        .dataWindowStartTimeStamp(dataWindowStartTimeStamp)
                        .dataWindowEndTimeStamp(dataWindowEndTimeStamp)
                        .clientAuctions(clientAuctions)
                        .instanceId(deploymentProperties.getPbsHostId())
                        .region(deploymentProperties.getPbsRegion())
                        .vendor(deploymentProperties.getPbsVendor())
                        .build())
                .collect(Collectors.toSet());

        logNotDeliveredLineItems(deliveryProgress, reportsBatch);
        return DeliveryProgressReportBatch.of(reportsBatch, reportId, dataWindowEndTimeStamp);
    }

    private DeliveryProgressReport.DeliveryProgressReportBuilder updateReportWithLineItems(
            DeliveryProgress deliveryProgress,
            List<org.prebid.server.deals.lineitem.LineItemStatus> lineItemStatuses,
            Map<String, org.prebid.server.deals.lineitem.LineItemStatus> overallLineItemStatuses,
            int lineItemsCount,
            int batchNumber,
            int batchSize,
            boolean isOverall) {
        final int startBatchIndex = batchNumber * batchSize;
        final int endBatchIndex = (batchNumber + 1) * batchSize;
        final List<org.prebid.server.deals.lineitem.LineItemStatus> batchList =
                lineItemStatuses.subList(startBatchIndex, Math.min(endBatchIndex, lineItemsCount));
        return DeliveryProgressReport.builder()
                .lineItemStatus(makeLineItemStatusReports(deliveryProgress, batchList,
                        overallLineItemStatuses, isOverall));
    }

    private Set<LineItemStatus> makeLineItemStatusReports(
            DeliveryProgress deliveryProgress,
            List<org.prebid.server.deals.lineitem.LineItemStatus> lineItemStatuses,
            Map<String, org.prebid.server.deals.lineitem.LineItemStatus> overallLineItemStatuses,
            boolean isOverall) {

        return lineItemStatuses.stream()
                .map(lineItemStatus -> toLineItemStatusReport(lineItemStatus,
                        overallLineItemStatuses != null
                                ? overallLineItemStatuses.get(lineItemStatus.getLineItemId())
                                : null,
                        deliveryProgress, isOverall))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static void logNotDeliveredLineItems(DeliveryProgress deliveryProgress,
                                                 Set<DeliveryProgressReport> reportsBatch) {
        final Set<String> reportedLineItems = reportsBatch.stream()
                .map(DeliveryProgressReport::getLineItemStatus)
                .flatMap(Collection::stream)
                .map(LineItemStatus::getLineItemId)
                .collect(Collectors.toSet());

        final String notDeliveredLineItems = deliveryProgress.getLineItemStatuses().keySet().stream()
                .filter(id -> !reportedLineItems.contains(id))
                .collect(Collectors.joining(", "));
        if (StringUtils.isNotBlank(notDeliveredLineItems)) {
            logger.info("Line item with id {0} will not be reported,"
                    + " as it does not have active delivery schedules during report window.", notDeliveredLineItems);
        }
    }

    DeliveryProgressReport updateReportTimeStamp(DeliveryProgressReport deliveryProgressReport, ZonedDateTime now) {
        return deliveryProgressReport.toBuilder().reportTimeStamp(formatTimeStamp(now)).build();
    }

    private LineItemStatus toLineItemStatusReport(org.prebid.server.deals.lineitem.LineItemStatus lineItemStatus,
                                                  org.prebid.server.deals.lineitem.LineItemStatus overallLineItemStatus,
                                                  DeliveryProgress deliveryProgress, boolean isOverall) {
        final String lineItemId = lineItemStatus.getLineItemId();
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        if (isOverall && lineItem == null) {
            return null;
        }
        final DeliveryPlan activeDeliveryPlan = ObjectUtil.getIfNotNull(lineItem, LineItem::getActiveDeliveryPlan);
        final Set<DeliverySchedule> deliverySchedules = deliverySchedule(lineItemStatus, overallLineItemStatus,
                activeDeliveryPlan);
        if (CollectionUtils.isEmpty(deliverySchedules) && !isOverall) {
            return null;
        }

        return LineItemStatus.builder()
                .lineItemSource(ObjectUtil.firstNonNull(lineItemStatus::getSource,
                        () -> ObjectUtil.getIfNotNull(lineItem, LineItem::getSource)))
                .lineItemId(lineItemId)
                .dealId(ObjectUtil.firstNonNull(lineItemStatus::getDealId,
                        () -> ObjectUtil.getIfNotNull(lineItem, LineItem::getDealId)))
                .extLineItemId(ObjectUtil.firstNonNull(lineItemStatus::getExtLineItemId,
                        () -> ObjectUtil.getIfNotNull(lineItem, LineItem::getExtLineItemId)))
                .accountAuctions(accountRequests(ObjectUtil.firstNonNull(lineItemStatus::getAccountId,
                        () -> ObjectUtil.getIfNotNull(lineItem, LineItem::getAccountId)), deliveryProgress))
                .domainMatched(lineItemStatus.getDomainMatched().sum())
                .targetMatched(lineItemStatus.getTargetMatched().sum())
                .targetMatchedButFcapped(lineItemStatus.getTargetMatchedButFcapped().sum())
                .targetMatchedButFcapLookupFailed(lineItemStatus.getTargetMatchedButFcapLookupFailed().sum())
                .pacingDeferred(lineItemStatus.getPacingDeferred().sum())
                .sentToBidder(lineItemStatus.getSentToBidder().sum())
                .sentToBidderAsTopMatch(lineItemStatus.getSentToBidderAsTopMatch().sum())
                .receivedFromBidder(lineItemStatus.getReceivedFromBidder().sum())
                .receivedFromBidderInvalidated(lineItemStatus.getReceivedFromBidderInvalidated().sum())
                .sentToClient(lineItemStatus.getSentToClient().sum())
                .sentToClientAsTopMatch(lineItemStatus.getSentToClientAsTopMatch().sum())
                .lostToLineItems(lostToLineItems(lineItemStatus, deliveryProgress))
                .events(lineItemStatus.getEvents())
                .deliverySchedule(deliverySchedules)
                .readyAt(isOverall ? toReadyAt(lineItem) : null)
                .spentTokens(isOverall && activeDeliveryPlan != null ? activeDeliveryPlan.getSpentTokens() : null)
                .pacingFrequency(isOverall && activeDeliveryPlan != null
                        ? activeDeliveryPlan.getDeliveryRateInMilliseconds()
                        : null)
                .build();
    }

    private String toReadyAt(LineItem lineItem) {
        final ZonedDateTime readyAt = ObjectUtil.getIfNotNull(lineItem, LineItem::getReadyAt);
        return readyAt != null ? UTC_MILLIS_FORMATTER.format(readyAt) : null;
    }

    private Long accountRequests(String accountId, DeliveryProgress deliveryProgress) {
        final LongAdder accountRequests = accountId != null
                ? deliveryProgress.getRequestsPerAccount().get(accountId)
                : null;
        return accountRequests != null ? accountRequests.sum() : null;
    }

    private Set<LostToLineItem> lostToLineItems(org.prebid.server.deals.lineitem.LineItemStatus lineItemStatus,
                                                DeliveryProgress deliveryProgress) {
        final Map<String, org.prebid.server.deals.lineitem.LostToLineItem> lostTo =
                deliveryProgress.getLineItemIdToLost().get(lineItemStatus.getLineItemId());

        if (lostTo != null) {
            return lostTo.values().stream()
                    .sorted(LOST_TO_LINE_ITEM_COMPARATOR.reversed())
                    .map(this::toLostToLineItems)
                    .limit(competitorsNumber)
                    .collect(Collectors.toSet());
        }

        return null;
    }

    private LostToLineItem toLostToLineItems(org.prebid.server.deals.lineitem.LostToLineItem lostToLineItem) {
        final String lineItemId = lostToLineItem.getLineItemId();
        return LostToLineItem.of(
                ObjectUtil.getIfNotNull(lineItemService.getLineItemById(lineItemId), LineItem::getSource), lineItemId,
                lostToLineItem.getCount().sum());
    }

    private static Set<DeliverySchedule> deliverySchedule(
            org.prebid.server.deals.lineitem.LineItemStatus lineItemStatus,
            org.prebid.server.deals.lineitem.LineItemStatus overallLineItemStatus,
            DeliveryPlan activeDeliveryPlan) {

        final Map<String, DeliveryPlan> idToDeliveryPlan = overallLineItemStatus != null
                ? overallLineItemStatus.getDeliveryPlans().stream()
                .collect(Collectors.toMap(DeliveryPlan::getPlanId, Function.identity()))
                : Collections.emptyMap();

        final Set<DeliverySchedule> deliverySchedules = lineItemStatus.getDeliveryPlans().stream()
                .map(deliveryPlan -> toDeliverySchedule(deliveryPlan, idToDeliveryPlan.get(deliveryPlan.getPlanId())))
                .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(deliverySchedules)) {
            if (activeDeliveryPlan != null) {
                deliverySchedules.add(DeliveryProgressReportFactory
                        .toDeliverySchedule(activeDeliveryPlan.withoutSpentTokens()));
            }
        }
        return deliverySchedules;
    }

    static DeliverySchedule toDeliverySchedule(DeliveryPlan deliveryPlan) {
        return toDeliverySchedule(deliveryPlan, null);
    }

    private static DeliverySchedule toDeliverySchedule(DeliveryPlan plan, DeliveryPlan overallPlan) {
        final Map<Integer, Long> priorityClassToTotalSpent = overallPlan != null
                ? overallPlan.getDeliveryTokens().stream()
                .collect(Collectors.toMap(DeliveryToken::getPriorityClass, deliveryToken -> deliveryToken.getSpent()
                        .sum()))
                : Collections.emptyMap();

        final Set<Token> tokens = plan.getDeliveryTokens().stream()
                .map(token -> Token.of(token.getPriorityClass(), token.getTotal(),
                        token.getSpent().sum(), priorityClassToTotalSpent.get(token.getPriorityClass())))
                .collect(Collectors.toSet());

        return DeliverySchedule.builder()
                .planId(plan.getPlanId())
                .planStartTimeStamp(formatTimeStamp(plan.getStartTimeStamp()))
                .planExpirationTimeStamp(formatTimeStamp(plan.getEndTimeStamp()))
                .planUpdatedTimeStamp(formatTimeStamp(plan.getUpdatedTimeStamp()))
                .tokens(tokens)
                .build();
    }

    private static String formatTimeStamp(ZonedDateTime zonedDateTime) {
        return zonedDateTime != null
                ? UTC_MILLIS_FORMATTER.format(zonedDateTime)
                : null;
    }

    private static class LostToLineItemComparator implements
            Comparator<org.prebid.server.deals.lineitem.LostToLineItem> {

        @Override
        public int compare(org.prebid.server.deals.lineitem.LostToLineItem lostToLineItem1,
                           org.prebid.server.deals.lineitem.LostToLineItem lostToLineItem2) {
            return Long.compare(lostToLineItem1.getCount().sum(), lostToLineItem2.getCount().sum());
        }
    }
}
