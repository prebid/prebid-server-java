package org.prebid.server.deals.lineitem;

import io.vertx.core.impl.ConcurrentHashSet;
import lombok.Value;
import org.prebid.server.deals.proto.report.Event;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
public class LineItemStatus {

    String lineItemId;

    String source;

    String dealId;

    String extLineItemId;

    String accountId;

    LongAdder domainMatched;

    LongAdder targetMatched;

    LongAdder targetMatchedButFcapped;

    LongAdder targetMatchedButFcapLookupFailed;

    LongAdder pacingDeferred;

    LongAdder sentToBidder;

    LongAdder sentToBidderAsTopMatch;

    LongAdder receivedFromBidder;

    LongAdder receivedFromBidderInvalidated;

    LongAdder sentToClient;

    LongAdder sentToClientAsTopMatch;

    Set<LostToLineItem> lostToLineItems;

    Set<Event> events;

    Set<DeliveryPlan> deliveryPlans;

    private LineItemStatus(String lineItemId, String source, String dealId, String extLineItemId, String accountId) {
        this.lineItemId = lineItemId;
        this.source = source;
        this.dealId = dealId;
        this.extLineItemId = extLineItemId;
        this.accountId = accountId;

        domainMatched = new LongAdder();
        targetMatched = new LongAdder();
        targetMatchedButFcapped = new LongAdder();
        targetMatchedButFcapLookupFailed = new LongAdder();
        pacingDeferred = new LongAdder();
        sentToBidder = new LongAdder();
        sentToBidderAsTopMatch = new LongAdder();
        receivedFromBidder = new LongAdder();
        receivedFromBidderInvalidated = new LongAdder();
        sentToClient = new LongAdder();
        sentToClientAsTopMatch = new LongAdder();

        lostToLineItems = new ConcurrentHashSet<>();
        events = new ConcurrentHashSet<>();
        deliveryPlans = new ConcurrentHashSet<>();
    }

    public static LineItemStatus of(String lineItemId, String source, String dealId, String extLineItemId,
                                    String accountId) {
        return new LineItemStatus(lineItemId, source, dealId, extLineItemId, accountId);
    }

    public static LineItemStatus of(LineItem lineItem) {
        return new LineItemStatus(lineItem.getLineItemId(), lineItem.getSource(), lineItem.getDealId(),
                lineItem.getExtLineItemId(), lineItem.getAccountId());
    }

    public static LineItemStatus of(String lineItemId) {
        return new LineItemStatus(lineItemId, null, null, null, null);
    }

    public void incDomainMatched() {
        domainMatched.increment();
    }

    public void incTargetMatched() {
        targetMatched.increment();
    }

    public void incTargetMatchedButFcapped() {
        targetMatchedButFcapped.increment();
    }

    public void incTargetMatchedButFcapLookupFailed() {
        targetMatchedButFcapLookupFailed.increment();
    }

    public void incPacingDeferred() {
        pacingDeferred.increment();
    }

    public void incSentToBidder() {
        sentToBidder.increment();
    }

    public void incSentToBidderAsTopMatch() {
        sentToBidderAsTopMatch.increment();
    }

    public void incReceivedFromBidder() {
        receivedFromBidder.increment();
    }

    public void incReceivedFromBidderInvalidated() {
        receivedFromBidderInvalidated.increment();
    }

    public void incSentToClient() {
        sentToClient.increment();
    }

    public void incSentToClientAsTopMatch() {
        sentToClientAsTopMatch.increment();
    }

    public void merge(LineItemStatus other) {
        domainMatched.add(other.domainMatched.sum());
        targetMatched.add(other.targetMatched.sum());
        targetMatchedButFcapped.add(other.targetMatchedButFcapped.sum());
        targetMatchedButFcapLookupFailed.add(other.getTargetMatchedButFcapLookupFailed().sum());
        pacingDeferred.add(other.pacingDeferred.sum());
        sentToBidder.add(other.sentToBidder.sum());
        sentToBidderAsTopMatch.add(other.sentToBidderAsTopMatch.sum());
        receivedFromBidder.add(other.receivedFromBidder.sum());
        receivedFromBidderInvalidated.add(other.receivedFromBidderInvalidated.sum());
        sentToClient.add(other.sentToClient.sum());
        sentToClientAsTopMatch.add(other.sentToClientAsTopMatch.sum());
        mergeEvents(other);
    }

    private void mergeEvents(LineItemStatus other) {
        final Map<String, Event> typesToEvent = other.events.stream()
                .collect(Collectors.toMap(Event::getType, Function.identity()));
        typesToEvent.forEach(this::addOrUpdateEvent);
    }

    private void addOrUpdateEvent(String type, Event distEvent) {
        final Event sameTypeEvent = events.stream()
                .filter(event -> event.getType().equals(type))
                .findFirst().orElse(null);
        if (sameTypeEvent != null) {
            sameTypeEvent.getCount().add(distEvent.getCount().sum());
        } else {
            events.add(distEvent);
        }
    }
}
