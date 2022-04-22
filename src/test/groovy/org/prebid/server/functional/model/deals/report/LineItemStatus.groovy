package org.prebid.server.functional.model.deals.report

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class LineItemStatus {

    String lineItemSource
    String lineItemId
    String dealId
    String extLineItemId
    Long accountAuctions
    Long domainMatched
    Long targetMatched
    Long targetMatchedButFcapped
    Long targetMatchedButFcapLookupFailed
    Long pacingDeferred
    Long sentToBidder
    Long sentToBidderAsTopMatch
    Long receivedFromBidder
    Long receivedFromBidderInvalidated
    Long sentToClient
    Long sentToClientAsTopMatch
    Set<LostToLineItem> lostToLineItems
    Set<Event> events
    Set<DeliverySchedule> deliverySchedule
    String readyAt
    Long spentTokens
    Long pacingFrequency
}
