package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.LineItemSize
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class PgBidResponseSpec extends BasePgSpec {

    def cleanup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should allow valid bidder response with deals info continue taking part in auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)
        def lineItemCount = plansResponse.lineItems.size()

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Set bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder returned valid response with deals info during auction"
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToClient?.size() == lineItemCount
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToClientAsTopMatch?.size() == lineItemCount
    }

    def "PBS should invalidate bidder response when bid id doesn't match to the bid request bid id"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Set bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse).tap {
            seatbid[0].bid[0].impid = PBSUtils.randomNumber as String
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder response is invalid"
        def bidderError = auctionResponse.ext?.errors?.get(GENERIC)
        assert bidderError?.size() == 1
        assert bidderError[0].message.startsWith("Bid \"${bidResponse.seatbid[0].bid[0].id}\" has no corresponding imp in request")
    }

    def "PBS should invalidate bidder response when deal id doesn't match to the bid request deal id"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Set bid response"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse).tap {
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber as String
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder response is invalid"
        def bidderError = auctionResponse.ext?.errors?.get(GENERIC)
        assert bidderError?.size() == 1
        assert bidderError[0].message.startsWith("WARNING: Bid \"${bidResponse.seatbid[0].bid[0].id}\" has 'dealid' not present in corresponding imp in request.")
    }

    def "PBS should invalidate bidder response when non-matched to the bid request size is returned"() {
        given: "Bid request with set sizes"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [Format.defaultFormat]
        }
        def impFormat = bidRequest.imp[0].banner.format[0]

        and: "Planner Mock line items"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Set bid response with unmatched to the bid request size"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse).tap {
            seatbid[0].bid[0].w = PBSUtils.randomNumber
        }
        def bid = bidResponse.seatbid[0].bid[0]
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder response is invalid"
        assert auctionResponse.ext?.debug?.pgmetrics?.responseInvalidated?.size() == plansResponse.lineItems.size()

        and: "PBS invalidated response as unmatched by size"
        def bidderError = auctionResponse.ext?.errors?.get(GENERIC)
        assert bidderError?.size() == 1
        assert bidderError[0].message == "Bid \"$bid.id\" has 'w' and 'h' not supported by " +
                "corresponding imp in request. Bid dimensions: '${bid.w}x$bid.h', formats in imp: '${impFormat.w}x$impFormat.h'"
    }

    def "PBS should invalidate bidder response when non-matched to the PBS line item size response is returned"() {
        given: "Bid request"
        def newFormat = new Format(w: PBSUtils.randomNumber, h: PBSUtils.randomNumber)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].banner.format = [Format.defaultFormat, newFormat]
        }

        and: "Planner Mock line items with a default size"
        def plansResponse = PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id).tap {
            lineItems[0].sizes = [LineItemSize.defaultLineItemSize]
        }
        def lineItemSize = plansResponse.lineItems[0].sizes[0]
        generalPlanner.initPlansResponse(plansResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Set bid response with non-matched to the line item size"
        def bidResponse = BidResponse.getDefaultPgBidResponse(bidRequest, plansResponse).tap {
            seatbid[0].bid[0].w = newFormat.w
            seatbid[0].bid[0].h = newFormat.h
        }
        def bid = bidResponse.seatbid[0].bid[0]
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder response is invalid"
        assert auctionResponse.ext?.debug?.pgmetrics?.responseInvalidated?.size() == plansResponse.lineItems.size()

        and: "PBS invalidated response as not matched by size"
        def bidderError = auctionResponse.ext?.errors?.get(GENERIC)
        assert bidderError?.size() == 1
        assert bidderError[0].message == "Bid \"$bid.id\" has 'w' and 'h' not matched to Line Item. " +
                "Bid dimensions: '${bid.w}x$bid.h', Line Item sizes: '${lineItemSize.w}x$lineItemSize.h'"
    }
}
