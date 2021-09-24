package org.prebid.server.deals.lineitem;

import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.proto.report.Event;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DeliveryProgress {

    private static final String WIN_EVENT_TYPE = "win";

    private final Map<String, LineItemStatus> lineItemStatuses;
    private final Map<String, LongAdder> requestsPerAccount;
    private final Map<String, Map<String, LostToLineItem>> lineItemIdToLost;
    private final LongAdder requests;
    private ZonedDateTime startTimeStamp;
    private ZonedDateTime endTimeStamp;
    private final LineItemService lineItemService;

    private DeliveryProgress(ZonedDateTime startTimeStamp, LineItemService lineItemService) {
        this.startTimeStamp = Objects.requireNonNull(startTimeStamp);
        this.lineItemStatuses = new ConcurrentHashMap<>();
        this.requests = new LongAdder();
        this.requestsPerAccount = new ConcurrentHashMap<>();
        this.lineItemIdToLost = new ConcurrentHashMap<>();
        this.lineItemService = Objects.requireNonNull(lineItemService);
    }

    public static DeliveryProgress of(ZonedDateTime startTimeStamp, LineItemService lineItemService) {
        return new DeliveryProgress(startTimeStamp, lineItemService);
    }

    public DeliveryProgress copyWithOriginalPlans() {
        final DeliveryProgress progress = DeliveryProgress.of(this.getStartTimeStamp(),
                this.lineItemService);

        for (final LineItemStatus originalStatus : this.lineItemStatuses.values()) {
            progress.lineItemStatuses.put(originalStatus.getLineItemId(), createStatusWithPlans(originalStatus));
        }

        progress.mergeFrom(this);

        return progress;
    }

    private LineItemStatus createStatusWithPlans(LineItemStatus originalStatus) {
        final LineItemStatus status = createLineItemStatus(originalStatus.getLineItemId());
        status.getDeliveryPlans().addAll(originalStatus.getDeliveryPlans());
        return status;
    }

    /**
     * Updates delivery progress from {@link TxnLog}.
     */
    public void recordTransactionLog(TxnLog txnLog, Map<String, Integer> planIdToTokenPriority, String accountId) {
        accountRequests(accountId).increment();
        requests.increment();

        txnLog.lineItemSentToClientAsTopMatch()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incSentToClientAsTopMatch));
        txnLog.lineItemsSentToClient()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incSentToClient));
        txnLog.lineItemsMatchedDomainTargeting()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incDomainMatched));
        txnLog.lineItemsMatchedWholeTargeting()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incTargetMatched));
        txnLog.lineItemsMatchedTargetingFcapped()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incTargetMatchedButFcapped));
        txnLog.lineItemsMatchedTargetingFcapLookupFailed()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incTargetMatchedButFcapLookupFailed));
        txnLog.lineItemsPacingDeferred()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incPacingDeferred));
        txnLog.lineItemsSentToBidder().values().forEach(idList -> idList
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incSentToBidder)));
        txnLog.lineItemsSentToBidderAsTopMatch().values().forEach(bidderList -> bidderList
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incSentToBidderAsTopMatch)));
        txnLog.lineItemsReceivedFromBidder().values().forEach(idList -> idList
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incReceivedFromBidder)));
        txnLog.lineItemsResponseInvalidated()
                .forEach(lineItemId -> increment(lineItemId, LineItemStatus::incReceivedFromBidderInvalidated));

        txnLog.lineItemSentToClientAsTopMatch()
                .forEach(lineItemId -> incToken(lineItemId, planIdToTokenPriority));

        txnLog.lostMatchingToLineItems().forEach((lineItemId, lostToLineItemsIds) ->
                updateLostToEachLineItem(lineItemId, lostToLineItemsIds, lineItemIdToLost));
        txnLog.lostAuctionToLineItems().forEach((lineItemId, lostToLineItemsIds) ->
                updateLostToEachLineItem(lineItemId, lostToLineItemsIds, lineItemIdToLost));
    }

    /**
     * Increments {@link LineItemStatus} win type {@link Event} counter. Creates new {@link LineItemStatus} if not
     * exists.
     */
    public void recordWinEvent(String lineItemId) {
        final LineItemStatus lineItemStatus = lineItemStatuses.computeIfAbsent(lineItemId, this::createLineItemStatus);
        final Event winEvent = lineItemStatus.getEvents().stream()
                .filter(event -> event.getType().equals(WIN_EVENT_TYPE))
                .findAny()
                .orElseGet(() -> Event.of(WIN_EVENT_TYPE, new LongAdder()));

        winEvent.getCount().increment();
        lineItemStatus.getEvents().add(winEvent);
    }

    private LineItemStatus createLineItemStatus(String lineItemId) {
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        return lineItem != null
                ? LineItemStatus.of(lineItem)
                : LineItemStatus.of(lineItemId);
    }

    /**
     * Updates delivery progress from another {@link DeliveryProgress}.
     */
    public void mergeFrom(DeliveryProgress another) {
        requests.add(another.requests.sum());

        another.requestsPerAccount.forEach((accountId, requestsCount) ->
                mergeRequestsCount(accountId, requestsCount, requestsPerAccount));

        another.lineItemStatuses.forEach((lineItemId, lineItemStatus) ->
                lineItemStatuses.computeIfAbsent(lineItemId, this::createLineItemStatus).merge(lineItemStatus));

        another.lineItemIdToLost.forEach((lineItemId, currentLineItemLost) ->
                mergeCurrentLineItemLostReportToOverall(lineItemId, currentLineItemLost, lineItemIdToLost));
    }

    public void upsertPlanReferenceFromLineItem(LineItem lineItem) {
        final String lineItemId = lineItem.getLineItemId();
        final LineItemStatus existingLineItemStatus = lineItemStatuses.get(lineItemId);
        final DeliveryPlan activeDeliveryPlan = lineItem.getActiveDeliveryPlan();
        if (existingLineItemStatus == null) {
            final LineItemStatus lineItemStatus = createLineItemStatus(lineItem.getLineItemId());
            lineItemStatus.getDeliveryPlans().add(activeDeliveryPlan);
            lineItemStatuses.put(lineItemId, lineItemStatus);
        } else {
            updateLineItemStatusWithActiveDeliveryPlan(existingLineItemStatus, activeDeliveryPlan);
        }
    }

    /**
     * Updates {@link LineItemStatus} with current {@link DeliveryPlan}.
     */
    public void mergePlanFromLineItem(LineItem lineItem) {
        final LineItemStatus currentLineItemStatus = lineItemStatuses.computeIfAbsent(lineItem.getLineItemId(),
                this::createLineItemStatus);
        final DeliveryPlan updatedDeliveryPlan = lineItem.getActiveDeliveryPlan();

        final Set<DeliveryPlan> deliveryPlans = currentLineItemStatus.getDeliveryPlans();
        final DeliveryPlan currentPlan = deliveryPlans.stream()
                .filter(plan -> Objects.equals(plan.getPlanId(), updatedDeliveryPlan.getPlanId()))
                .findFirst()
                .orElse(null);

        if (currentPlan == null) {
            deliveryPlans.add(updatedDeliveryPlan.withoutSpentTokens());
        } else if (currentPlan.isUpdated(updatedDeliveryPlan.getDeliverySchedule())) {
            final DeliveryPlan updatedPlan = currentPlan.mergeWithNextDeliveryPlan(updatedDeliveryPlan);
            deliveryPlans.remove(currentPlan);
            deliveryPlans.add(updatedPlan);
        }
    }

    /**
     * Remove stale {@link LineItemStatus} from statistic.
     */
    public void cleanLineItemStatuses(ZonedDateTime now, long lineItemStatusTtl, int maxPlanNumberInDeliveryProgress) {
        lineItemStatuses.entrySet().removeIf(entry -> isLineItemStatusExpired(entry.getKey(), now, lineItemStatusTtl));

        lineItemStatuses.values().forEach(
                lineItemStatus -> cutCachedDeliveryPlans(lineItemStatus, maxPlanNumberInDeliveryProgress));
    }

    /**
     * Returns true when lineItem is not in metaData and it is expired for more then defined in configuration time.
     */
    private boolean isLineItemStatusExpired(String lineItemId, ZonedDateTime now, long lineItemStatusTtl) {
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);

        return lineItem == null || ChronoUnit.MILLIS.between(lineItem.getEndTimeStamp(), now) > lineItemStatusTtl;
    }

    /**
     * Cuts number of plans in {@link LineItemStatus} from overall statistic by number defined in configuration.
     */
    private void cutCachedDeliveryPlans(LineItemStatus lineItemStatus, int maxPlanNumberInDeliveryProgress) {
        final Set<DeliveryPlan> deliveryPlans = lineItemStatus.getDeliveryPlans();
        if (deliveryPlans.size() > maxPlanNumberInDeliveryProgress) {
            final Set<DeliveryPlan> plansToRemove = deliveryPlans.stream()
                    .sorted(Comparator.comparing(DeliveryPlan::getEndTimeStamp))
                    .limit(deliveryPlans.size() - maxPlanNumberInDeliveryProgress)
                    .collect(Collectors.toSet());
            plansToRemove.forEach(deliveryPlans::remove);
        }
    }

    /**
     * Updates {@link LineItemStatus} with active {@link DeliveryPlan}.
     */
    private void updateLineItemStatusWithActiveDeliveryPlan(LineItemStatus lineItemStatus,
                                                            DeliveryPlan updatedDeliveryPlan) {
        final Set<DeliveryPlan> deliveryPlans = lineItemStatus.getDeliveryPlans();
        final DeliveryPlan currentPlan = deliveryPlans.stream()
                .filter(plan -> Objects.equals(plan.getPlanId(), updatedDeliveryPlan.getPlanId()))
                .filter(plan -> plan.isUpdated(updatedDeliveryPlan.getDeliverySchedule()))
                .findAny()
                .orElse(null);
        if (currentPlan != null) {
            if (!Objects.equals(currentPlan.getUpdatedTimeStamp(), updatedDeliveryPlan.getUpdatedTimeStamp())) {
                deliveryPlans.add(updatedDeliveryPlan);
                deliveryPlans.remove(currentPlan);
            }
        } else {
            deliveryPlans.add(updatedDeliveryPlan);
        }
    }

    public void updateWithActiveLineItems(Collection<LineItem> lineItems) {
        lineItems.forEach(lineItem -> lineItemStatuses.putIfAbsent(lineItem.getLineItemId(),
                createLineItemStatus(lineItem.getLineItemId())));
    }

    public Map<String, LineItemStatus> getLineItemStatuses() {
        return lineItemStatuses;
    }

    public Map<String, LongAdder> getRequestsPerAccount() {
        return requestsPerAccount;
    }

    public Map<String, Map<String, LostToLineItem>> getLineItemIdToLost() {
        return lineItemIdToLost;
    }

    public LongAdder getRequests() {
        return requests;
    }

    public ZonedDateTime getStartTimeStamp() {
        return startTimeStamp;
    }

    public void setStartTimeStamp(ZonedDateTime startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
    }

    public void setEndTimeStamp(ZonedDateTime endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
    }

    public ZonedDateTime getEndTimeStamp() {
        return endTimeStamp;
    }

    private LongAdder accountRequests(String account) {
        return requestsPerAccount.computeIfAbsent(account, ignored -> new LongAdder());
    }

    /**
     * Increments {@link LineItemStatus} metric, creates line item status if does not exist.
     */
    private void increment(String lineItemId, Consumer<LineItemStatus> inc) {
        inc.accept(lineItemStatuses.computeIfAbsent(lineItemId, this::createLineItemStatus));
    }

    /**
     * Increment tokens in active delivery report.
     */
    private void incToken(String lineItemId, Map<String, Integer> planIdToTokenPriority) {
        final LineItemStatus lineItemStatus = lineItemStatuses.get(lineItemId);
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        final DeliveryPlan lineItemActivePlan = lineItem.getActiveDeliveryPlan();
        if (lineItemActivePlan != null) {
            DeliveryPlan reportActivePlan = lineItemStatus.getDeliveryPlans().stream()
                    .filter(plan -> Objects.equals(plan.getPlanId(), lineItemActivePlan.getPlanId()))
                    .findFirst()
                    .orElse(null);
            if (reportActivePlan == null) {
                reportActivePlan = lineItemActivePlan.withoutSpentTokens();
                lineItemStatus.getDeliveryPlans().add(reportActivePlan);
            }

            final Integer tokenPriority = planIdToTokenPriority.get(reportActivePlan.getPlanId());
            if (tokenPriority != null) {
                reportActivePlan.incTokenWithPriority(tokenPriority);
            }
        }
    }

    /**
     * Updates lostToLineItem metric for line item specified by lineItemId parameter against line item ids from
     * parameter lostToLineItemIds
     */
    private void updateLostToEachLineItem(String lineItemId, Set<String> lostToLineItemsIds,
                                          Map<String, Map<String, LostToLineItem>> lostToLineItemTimes) {
        final Map<String, LostToLineItem> lostToLineItemsTimes = lostToLineItemTimes
                .computeIfAbsent(lineItemId, key -> new ConcurrentHashMap<>());
        lostToLineItemsIds.forEach(lostToLineItemId -> incLostToLineItemTimes(lostToLineItemId, lostToLineItemsTimes));
    }

    /**
     * Updates listToLineItem metric against line item specified in parameter lostToLineItemId
     */
    private void incLostToLineItemTimes(String lostToLineItemId, Map<String, LostToLineItem> lostToLineItemsTimes) {
        final LostToLineItem lostToLineItem = lostToLineItemsTimes.computeIfAbsent(lostToLineItemId,
                ignored -> LostToLineItem.of(lostToLineItemId, new LongAdder()));
        lostToLineItem.getCount().increment();
    }

    /**
     * Merges requests per account to overall statistics.
     */
    private void mergeRequestsCount(String accountId, LongAdder requestsCount,
                                    Map<String, LongAdder> requestsPerAccount) {
        requestsPerAccount.computeIfPresent(accountId, (key, oldValue) -> {
            oldValue.add(requestsCount.sum());
            return oldValue;
        });
        requestsPerAccount.putIfAbsent(accountId, requestsCount);
    }

    private void mergeCurrentLineItemLostReportToOverall(
            String lineItemId,
            Map<String, LostToLineItem> currentLineItemLost,
            Map<String, Map<String, LostToLineItem>> overallLineItemIdToLost) {
        final Map<String, LostToLineItem> overallLineItemLost = overallLineItemIdToLost
                .computeIfAbsent(lineItemId, ignored -> new ConcurrentHashMap<>());
        currentLineItemLost.forEach((lineItemIdLostTo, currentLostToLineItem) ->
                overallLineItemLost.merge(lineItemIdLostTo, currentLostToLineItem, this::addToCount)
        );
    }

    private LostToLineItem addToCount(LostToLineItem mergeTo, LostToLineItem mergeFrom) {
        mergeTo.getCount().add(mergeFrom.getCount().sum());
        return mergeTo;
    }
}
