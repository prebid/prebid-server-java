package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.Token
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class TokenSpec extends BasePgSpec {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should start using line item in auction when its expired tokens number is increased"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock zero tokens line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].deliverySchedules[0].tokens[0].total = 0
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested"
        def firstAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start processing PG deals"
        assert firstAuctionResponse.ext?.debug?.pgmetrics?.pacingDeferred ==
                [plansResponse.lineItems[0].lineItemId] as Set
        assert !firstAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder

        when: "Line item tokens are updated"
        plansResponse.lineItems[0].deliverySchedules[0].tokens[0].total = 1
        plansResponse.lineItems[0].deliverySchedules[0].updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).plusSeconds(1)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Updated line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Auction is requested for the second time"
        bidder.setResponse(bidRequest.id, bidResponse)
        def secondAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should process PG deals"
        def sentToBidder = secondAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == plansResponse.lineItems.size()
        assert sentToBidder[0] == plansResponse.lineItems[0].lineItemId
    }

    def "PBS shouldn't allow line items with zero token number take part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock zero tokens line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].deliverySchedules[0].tokens[0].total = 0
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should recognize line items with pacing deferred"
        assert auctionResponse.ext?.debug?.pgmetrics?.pacingDeferred == [plansResponse.lineItems[0].lineItemId] as Set
    }

    def "PBS should allow line item take part in auction when it has at least one unspent token among all expired tokens"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line item with zero and 1 available tokens"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            def deliverySchedules = lineItems[0].deliverySchedules[0]
            deliverySchedules.tokens[0].total = 0
            deliverySchedules.tokens << new Token(priorityClass: 2, total: 0)
            deliverySchedules.tokens << new Token(priorityClass: 3, total: 1)
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()
        def lineItemCount = plansResponse.lineItems.size()

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should process PG deals"
        assert !auctionResponse.ext?.debug?.pgmetrics?.pacingDeferred
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == lineItemCount
        def sentToBidder = auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == lineItemCount
        assert sentToBidder[0] == plansResponse.lineItems[0].lineItemId
    }

    def "PBS shouldn't allow line item take part in auction when all its tokens are spent"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock with 1 token to spend line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].deliverySchedules[0].tokens[0].total = 1
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Auction is happened for the first time"
        pgPbsService.sendAuctionRequest(bidRequest)

        when: "Requesting auction for the second time"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start PG processing"
        assert auctionResponse.ext?.debug?.pgmetrics?.pacingDeferred == [plansResponse.lineItems[0].lineItemId] as Set
    }

    def "PBS should take only the first token among tokens with the same priority class"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line item with 2 tokens of the same priority but the first has zero total number"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            def tokens = [new Token(priorityClass: 1, total: 0), new Token(priorityClass: 1, total: 1)]
            lineItems[0].deliverySchedules[0].tokens = tokens
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start PG processing as it was processed only the first token with 0 total number"
        assert auctionResponse.ext?.debug?.pgmetrics?.pacingDeferred == [plansResponse.lineItems[0].lineItemId] as Set
    }

    def "PBS shouldn't allow line item take part in auction when its number of available impressions is ahead of the scheduled time"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line item to have max 2 impressions during one week"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].deliverySchedules[0].tokens[0].total = 2
            lineItems[0].startTimeStamp = ZonedDateTime.now(ZoneId.from(UTC))
            lineItems[0].updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC))
            lineItems[0].endTimeStamp = ZonedDateTime.now(ZoneId.from(UTC)).plusWeeks(1)
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested for the first time"
        def firstAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS processed PG deals"
        def sentToBidder = firstAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == plansResponse.lineItems.size()
        assert sentToBidder[0] == plansResponse.lineItems[0].lineItemId

        when: "Auction is requested for the second time"
        def secondAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't allowed line item take part in auction as it has only one impression left to be shown during the week"
        assert secondAuctionResponse.ext?.debug?.pgmetrics?.pacingDeferred ==
                [plansResponse.lineItems[0].lineItemId] as Set
        assert !secondAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder
    }

    def "PBS should abandon line item with updated zero available token number take part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock with not null tokens number line item"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].deliverySchedules[0].tokens[0].total = 2
        }
        generalPlanner.initPlansResponse(plansResponse)

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is requested"
        def firstAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS processed PG deals"
        def sentToBidder = firstAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)
        assert sentToBidder?.size() == plansResponse.lineItems.size()
        assert sentToBidder[0] == plansResponse.lineItems[0].lineItemId

        when: "Line item tokens are updated to have no available tokens"
        plansResponse.lineItems[0].deliverySchedules[0].tokens[0].total = 0
        plansResponse.lineItems[0].deliverySchedules[0].updatedTimeStamp = ZonedDateTime.now(ZoneId.from(UTC))

        generalPlanner.initPlansResponse(plansResponse)

        and: "Updated line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Auction is requested for the second time"
        def secondAuctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't start processing PG deals"
        assert secondAuctionResponse.ext?.debug?.pgmetrics?.pacingDeferred ==
                [plansResponse.lineItems[0].lineItemId] as Set
        assert !secondAuctionResponse.ext?.debug?.pgmetrics?.sentToBidder
    }
}
