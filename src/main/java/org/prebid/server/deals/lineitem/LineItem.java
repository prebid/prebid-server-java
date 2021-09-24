package org.prebid.server.deals.lineitem;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.FrequencyCap;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.LineItemSize;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.deals.targeting.TargetingDefinition;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class LineItem {

    private static final Logger logger = LoggerFactory.getLogger(LineItem.class);

    private final LineItemMetaData metaData;

    private final Price normalizedPrice;

    private final List<String> fcapIds;

    private final TargetingDefinition targetingDefinition;

    private final AtomicReference<DeliveryPlan> activeDeliveryPlan;

    private final AtomicReference<ZonedDateTime> readyAt;

    private LineItem(LineItemMetaData metaData, Price normalizedPrice, TargetingDefinition targetingDefinition) {
        this.metaData = Objects.requireNonNull(metaData);
        this.normalizedPrice = normalizedPrice;
        this.targetingDefinition = targetingDefinition;

        this.fcapIds = extractFcapIds(metaData);

        activeDeliveryPlan = new AtomicReference<>();
        readyAt = new AtomicReference<>();
    }

    private LineItem(LineItemMetaData metaData,
                     Price normalizedPrice,
                     TargetingDefinition targetingDefinition,
                     ZonedDateTime readyAt,
                     ZonedDateTime now,
                     DeliveryPlan currentPlan) {
        this(metaData, normalizedPrice, targetingDefinition);
        this.readyAt.set(readyAt);

        updateOrAdvanceActivePlan(now, true, currentPlan);
    }

    private LineItem(LineItemMetaData metaData,
                     Price normalizedPrice,
                     TargetingDefinition targetingDefinition,
                     ZonedDateTime now) {
        this(metaData, normalizedPrice, targetingDefinition, null, now, null);
    }

    public static LineItem of(LineItemMetaData metaData,
                              Price normalizedPrice,
                              TargetingDefinition targetingDefinition,
                              ZonedDateTime now) {
        return new LineItem(metaData, normalizedPrice, targetingDefinition, now);
    }

    public LineItem withUpdatedMetadata(LineItemMetaData metaData,
                                        Price normalizedPrice,
                                        TargetingDefinition targetingDefinition,
                                        ZonedDateTime readyAt,
                                        ZonedDateTime now) {
        return new LineItem(metaData, normalizedPrice, targetingDefinition, readyAt, now, getActiveDeliveryPlan());
    }

    public void advanceToNextPlan(ZonedDateTime now, boolean isPlannerResponsive) {
        updateOrAdvanceActivePlan(now, isPlannerResponsive, getActiveDeliveryPlan());
    }

    /**
     * Increments tokens in {@link DeliveryToken} with highest priority within {@link DeliveryPlan}.
     *
     * @return class of the token incremented.
     */
    public Integer incSpentToken(ZonedDateTime now) {
        return incSpentToken(now, 0);
    }

    public Integer incSpentToken(ZonedDateTime now, long adjustment) {
        final DeliveryPlan deliveryPlan = activeDeliveryPlan.get();

        if (deliveryPlan != null) {
            final Integer tokenClassIncremented = deliveryPlan.incSpentToken();
            ZonedDateTime readyAtNewValue = deliveryPlan.calculateReadyAt();
            readyAtNewValue = readyAtNewValue != null && adjustment != 0
                    ? readyAtNewValue.plusNanos(TimeUnit.MILLISECONDS.toNanos(adjustment))
                    : readyAtNewValue;
            readyAt.set(readyAtNewValue);
            if (logger.isDebugEnabled()) {
                logger.debug("ReadyAt for lineItem {0} plan {1} was updated to {2} after token was spent. Total number"
                                + " of unspent token is {3}. Current time is {4}",
                        getLineItemId(), deliveryPlan.getPlanId(),
                        readyAt.get(), deliveryPlan.getUnspentTokens(), now);
            }
            return tokenClassIncremented;
        }
        return null;
    }

    public Integer getHighestUnspentTokensClass() {
        final DeliveryPlan activeDeliveryPlan = getActiveDeliveryPlan();
        return activeDeliveryPlan != null ? activeDeliveryPlan.getHighestUnspentTokensClass() : null;
    }

    public boolean isActive(ZonedDateTime now) {
        return dateBetween(now, metaData.getStartTimeStamp(), metaData.getEndTimeStamp());
    }

    public DeliveryPlan getActiveDeliveryPlan() {
        return activeDeliveryPlan.get();
    }

    public ZonedDateTime getReadyAt() {
        return readyAt.get();
    }

    public BigDecimal getCpm() {
        if (normalizedPrice != null) {
            return normalizedPrice.getCpm();
        }
        return null;
    }

    public String getCurrency() {
        if (normalizedPrice != null) {
            return normalizedPrice.getCurrency();
        }
        return null;
    }

    public String getLineItemId() {
        return metaData.getLineItemId();
    }

    public String getExtLineItemId() {
        return metaData.getExtLineItemId();
    }

    public String getDealId() {
        return metaData.getDealId();
    }

    public String getAccountId() {
        return metaData.getAccountId();
    }

    public String getSource() {
        return metaData.getSource();
    }

    public Integer getRelativePriority() {
        return metaData.getRelativePriority();
    }

    public ZonedDateTime getEndTimeStamp() {
        return metaData.getEndTimeStamp();
    }

    public ZonedDateTime getStartTimeStamp() {
        return metaData.getStartTimeStamp();
    }

    public ZonedDateTime getUpdatedTimeStamp() {
        return metaData.getUpdatedTimeStamp();
    }

    public List<FrequencyCap> getFrequencyCaps() {
        return metaData.getFrequencyCaps();
    }

    public List<LineItemSize> getSizes() {
        return metaData.getSizes();
    }

    public TargetingDefinition getTargetingDefinition() {
        return targetingDefinition;
    }

    public List<String> getFcapIds() {
        return fcapIds;
    }

    private static List<String> extractFcapIds(LineItemMetaData metaData) {
        return CollectionUtils.emptyIfNull(metaData.getFrequencyCaps()).stream()
                .map(FrequencyCap::getFcapId)
                .collect(Collectors.toList());
    }

    private void updateOrAdvanceActivePlan(ZonedDateTime now, boolean isPlannerResponsive, DeliveryPlan currentPlan) {
        final DeliverySchedule currentSchedule = ListUtils.emptyIfNull(metaData.getDeliverySchedules()).stream()
                .filter(schedule -> dateBetween(now, schedule.getStartTimeStamp(), schedule.getEndTimeStamp()))
                .findFirst()
                .orElse(null);

        if (currentSchedule != null) {
            final DeliveryPlan resolvedPlan = resolveActivePlan(currentPlan, currentSchedule, isPlannerResponsive);
            final ZonedDateTime readyAtBeforeUpdate = readyAt.get();
            if (currentPlan != resolvedPlan) {
                readyAt.set(currentPlan == null || !Objects.equals(currentSchedule.getPlanId(), currentPlan.getPlanId())
                        ? calculateReadyAfterMovingToNextPlan(now, resolvedPlan)
                        : calculateReadyAtAfterPlanUpdated(now, resolvedPlan));
                logger.info("ReadyAt for Line Item `{0}` was updated from plan {1} to {2} and readyAt from {3} to {4}"
                                + " at time is {5}", getLineItemId(),
                        currentPlan != null ? currentPlan.getPlanId() : " no plan ", resolvedPlan.getPlanId(),
                        readyAtBeforeUpdate, getReadyAt(), now);
                if (logger.isDebugEnabled()) {
                    logger.debug("Unspent tokens number for plan {0} is {1}", resolvedPlan.getPlanId(),
                            resolvedPlan.getUnspentTokens());
                }
            }
            activeDeliveryPlan.set(resolvedPlan);
        } else {
            activeDeliveryPlan.set(null);
            readyAt.set(null);
            logger.info("Active plan for Line Item `{0}` was not found at time is {1}, readyAt updated with 'never',"
                    + " until active plan become available", getLineItemId(), now);
        }
    }

    private ZonedDateTime calculateReadyAtAfterPlanUpdated(ZonedDateTime now, DeliveryPlan resolvedPlan) {
        final ZonedDateTime resolvedReadyAt = resolvedPlan.calculateReadyAt();
        logger.debug("Current plan for Line Item `{0}` was considered as updated from GP response and readyAt will be "
                + "updated from {1} to {2} at time is {3}", getLineItemId(), getReadyAt(), resolvedReadyAt, now);
        return resolvedReadyAt;
    }

    private ZonedDateTime calculateReadyAfterMovingToNextPlan(ZonedDateTime now, DeliveryPlan resolvedPlan) {
        return resolvedPlan.getDeliveryTokens().stream().anyMatch(deliveryToken -> deliveryToken.getTotal() > 0)
                ? now
                : null;
    }

    private static DeliveryPlan resolveActivePlan(DeliveryPlan currentPlan,
                                                  DeliverySchedule currentSchedule,
                                                  boolean isPlannerResponsive) {
        if (currentPlan != null) {
            if (Objects.equals(currentPlan.getPlanId(), currentSchedule.getPlanId())) {
                return currentPlan.getUpdatedTimeStamp().isBefore(currentSchedule.getUpdatedTimeStamp())
                        ? currentPlan.mergeWithNextDeliverySchedule(currentSchedule, false)
                        : currentPlan;
            } else if (!isPlannerResponsive) {
                return currentPlan.mergeWithNextDeliverySchedule(currentSchedule, true);
            }
        }

        return DeliveryPlan.of(currentSchedule);
    }

    /**
     * Returns true when now parameter is after startDate and before expirationDate.
     */
    private static boolean dateBetween(ZonedDateTime now, ZonedDateTime startDate, ZonedDateTime expirationDate) {
        return (now.isEqual(startDate) || now.isAfter(startDate)) && now.isBefore(expirationDate);
    }
}
