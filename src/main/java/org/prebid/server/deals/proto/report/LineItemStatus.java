package org.prebid.server.deals.proto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder
@Value
public class LineItemStatus {

    @JsonProperty("lineItemSource")
    String lineItemSource;

    @JsonProperty("lineItemId")
    String lineItemId;

    @JsonProperty("dealId")
    String dealId;

    @JsonProperty("extLineItemId")
    String extLineItemId;

    @JsonProperty("accountAuctions")
    Long accountAuctions;

    @JsonProperty("domainMatched")
    Long domainMatched;

    @JsonProperty("targetMatched")
    Long targetMatched;

    @JsonProperty("targetMatchedButFcapped")
    Long targetMatchedButFcapped;

    @JsonProperty("targetMatchedButFcapLookupFailed")
    Long targetMatchedButFcapLookupFailed;

    @JsonProperty("pacingDeferred")
    Long pacingDeferred;

    @JsonProperty("sentToBidder")
    Long sentToBidder;

    @JsonProperty("sentToBidderAsTopMatch")
    Long sentToBidderAsTopMatch;

    @JsonProperty("receivedFromBidder")
    Long receivedFromBidder;

    @JsonProperty("receivedFromBidderInvalidated")
    Long receivedFromBidderInvalidated;

    @JsonProperty("sentToClient")
    Long sentToClient;

    @JsonProperty("sentToClientAsTopMatch")
    Long sentToClientAsTopMatch;

    @JsonProperty("lostToLineItems")
    Set<LostToLineItem> lostToLineItems;

    Set<Event> events;

    @JsonProperty("deliverySchedule")
    Set<DeliverySchedule> deliverySchedule;

    @JsonProperty("readyAt")
    String readyAt;

    @JsonProperty("spentTokens")
    Long spentTokens;

    @JsonProperty("pacingFrequency")
    Long pacingFrequency;
}
