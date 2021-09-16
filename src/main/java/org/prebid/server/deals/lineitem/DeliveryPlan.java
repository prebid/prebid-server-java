package org.prebid.server.deals.lineitem;

import org.apache.commons.collections4.SetUtils;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.exception.PreBidException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DeliveryPlan {

    private final DeliverySchedule deliverySchedule;

    private final Set<DeliveryToken> deliveryTokens;

    private DeliveryPlan(DeliverySchedule deliverySchedule) {
        this(Objects.requireNonNull(deliverySchedule), toDeliveryTokens(deliverySchedule.getTokens()));
    }

    private DeliveryPlan(DeliverySchedule deliverySchedule, Set<DeliveryToken> deliveryTokens) {
        this.deliverySchedule = Objects.requireNonNull(deliverySchedule);
        this.deliveryTokens = Objects.requireNonNull(deliveryTokens);
    }

    public static DeliveryPlan of(DeliverySchedule deliverySchedule) {
        return new DeliveryPlan(deliverySchedule);
    }

    /**
     * Returns number of not spent tokens in {@link DeliveryPlan}.
     */
    public int getUnspentTokens() {
        return deliveryTokens.stream().mapToInt(DeliveryToken::getUnspent).sum();
    }

    /**
     * Returns number of spent tokens in {@link DeliveryPlan}.
     */
    public long getSpentTokens() {
        return deliveryTokens.stream().map(DeliveryToken::getSpent).mapToLong(LongAdder::sum).sum();
    }

    public long getTotalTokens() {
        return deliveryTokens.stream().mapToLong(DeliveryToken::getTotal).sum();
    }

    /**
     * Returns lowest (which means highest priority) token's class value with unspent tokens.
     */
    public int getHighestUnspentTokensClass() {
        return deliveryTokens.stream()
                .filter(token -> token.getUnspent() > 0)
                .findFirst()
                .map(DeliveryToken::getPriorityClass)
                .orElseThrow(() -> new PreBidException(String.format(
                        "Class with not spent tokens was not found for plan with id %s",
                        deliverySchedule.getPlanId())));
    }

    /**
     * Increments tokens in {@link DeliveryToken} with highest priority within {@link DeliveryPlan}
     *
     * @return class of the token incremented
     */
    public Integer incSpentToken() {
        final DeliveryToken unspentToken = deliveryTokens.stream()
                .filter(token -> token.getUnspent() > 0)
                .findFirst()
                .orElse(null);
        if (unspentToken != null) {
            unspentToken.inc();
            return unspentToken.getPriorityClass();
        }

        return null;
    }

    /**
     * Merges tokens from expired {@link DeliveryPlan} to the next one.
     */
    public DeliveryPlan mergeWithNextDeliverySchedule(DeliverySchedule nextDeliverySchedule, boolean sumTotal) {

        final Map<Integer, Token> nextTokensByClass = nextDeliverySchedule.getTokens().stream()
                .collect(Collectors.toMap(Token::getPriorityClass, Function.identity()));

        final Set<DeliveryToken> mergedTokens = new TreeSet<>();

        for (final DeliveryToken expiredToken : deliveryTokens) {
            final Integer priorityClass = expiredToken.getPriorityClass();
            final Token nextToken = nextTokensByClass.get(priorityClass);

            mergedTokens.add(expiredToken.mergeWithToken(nextToken, sumTotal));

            nextTokensByClass.remove(priorityClass);
        }

        // add remaining (not merged) tokens
        nextTokensByClass.values().stream().map(DeliveryToken::of).forEach(mergedTokens::add);

        return new DeliveryPlan(nextDeliverySchedule, mergedTokens);
    }

    public DeliveryPlan mergeWithNextDeliveryPlan(DeliveryPlan anotherPlan) {
        return mergeWithNextDeliverySchedule(anotherPlan.deliverySchedule, false);
    }

    public DeliveryPlan withoutSpentTokens() {
        return new DeliveryPlan(deliverySchedule, deliveryTokens.stream()
                .map(DeliveryToken::of)
                .collect(Collectors.toSet()));
    }

    public void incTokenWithPriority(Integer tokenPriority) {
        deliveryTokens.stream()
                .filter(token -> Objects.equals(token.getPriorityClass(), tokenPriority))
                .findAny()
                .ifPresent(DeliveryToken::inc);
    }

    /**
     * Calculates readyAt from expirationDate and number of unspent tokens.
     */
    public ZonedDateTime calculateReadyAt() {
        final ZonedDateTime planStartTime = deliverySchedule.getStartTimeStamp();
        final long spentTokens = getSpentTokens();
        final long unspentTokens = getUnspentTokens();
        final long timeShift = spentTokens * ((deliverySchedule.getEndTimeStamp().toInstant().toEpochMilli()
                - planStartTime.toInstant().toEpochMilli()) / getTotalTokens());
        return unspentTokens > 0
                ? ZonedDateTime.ofInstant(planStartTime.toInstant().plusMillis(timeShift), ZoneOffset.UTC)
                : null;
    }

    public Long getDeliveryRateInMilliseconds() {
        final int unspentTokens = getUnspentTokens();
        return unspentTokens > 0
                ? (deliverySchedule.getEndTimeStamp().toInstant().toEpochMilli()
                - deliverySchedule.getStartTimeStamp().toInstant().toEpochMilli())
                / getTotalTokens()
                : null;
    }

    public boolean isUpdated(DeliverySchedule deliverySchedule) {
        final ZonedDateTime currentPlanUpdatedDate = this.deliverySchedule.getUpdatedTimeStamp();
        final ZonedDateTime newPlanUpdatedDate = deliverySchedule.getUpdatedTimeStamp();
        return !(currentPlanUpdatedDate == null && newPlanUpdatedDate == null)
                && (currentPlanUpdatedDate == null || newPlanUpdatedDate == null
                || currentPlanUpdatedDate.isBefore(newPlanUpdatedDate));
    }

    public String getPlanId() {
        return deliverySchedule.getPlanId();
    }

    public ZonedDateTime getStartTimeStamp() {
        return deliverySchedule.getStartTimeStamp();
    }

    public ZonedDateTime getEndTimeStamp() {
        return deliverySchedule.getEndTimeStamp();
    }

    public ZonedDateTime getUpdatedTimeStamp() {
        return deliverySchedule.getUpdatedTimeStamp();
    }

    public Set<DeliveryToken> getDeliveryTokens() {
        return deliveryTokens;
    }

    public DeliverySchedule getDeliverySchedule() {
        return deliverySchedule;
    }

    private static Set<DeliveryToken> toDeliveryTokens(Set<Token> tokens) {
        return SetUtils.emptyIfNull(tokens).stream()
                .map(DeliveryToken::of)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
