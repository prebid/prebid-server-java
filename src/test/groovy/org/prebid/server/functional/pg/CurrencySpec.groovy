package org.prebid.server.functional.pg

import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.lineitem.Price
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class CurrencySpec extends BasePgSpec {

    def setup() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should convert non-default line item currency to the default one during the bidder auction"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items with the same CPM but different currencies"
        def defaultCurrency = Price.defaultPrice.currency
        def nonDefaultCurrency = "EUR"
        def defaultCurrencyLineItem = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: defaultCurrency) }]
        def nonDefaultCurrencyLineItems = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: nonDefaultCurrency) },
                                           LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: nonDefaultCurrency) },
                                           LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: nonDefaultCurrency) }]
        def lineItems = defaultCurrencyLineItem + nonDefaultCurrencyLineItems
        def plansResponse = new PlansResponse(lineItems: lineItems)
        generalPlanner.initPlansResponse(plansResponse)
        def nonDefaultCurrencyLineItemIds = nonDefaultCurrencyLineItems.collect { it.lineItemId }

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is requested"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "All line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == plansResponse.lineItems.size()

        and: "Line Item with EUR defaultCurrency was sent to bidder as EUR defaultCurrency rate > than USD"
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)?.sort() ==
                nonDefaultCurrencyLineItemIds.sort()
    }

    def "PBS should invalidate line item with an unknown for the conversion rate currency"() {
        given: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest
        def accountId = bidRequest.site.publisher.id

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Planner Mock line items with a default currency and unknown currency"
        def defaultCurrency = Price.defaultPrice.currency
        def unknownCurrency = "UAH"
        def defaultCurrencyLineItem = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: defaultCurrency) }]
        def unknownCurrencyLineItem = [LineItem.getDefaultLineItem(accountId).tap { price = new Price(cpm: 1, currency: unknownCurrency) }]
        def lineItems = defaultCurrencyLineItem + unknownCurrencyLineItem
        def plansResponse = new PlansResponse(lineItems: lineItems)
        generalPlanner.initPlansResponse(plansResponse)
        def defaultCurrencyLineItemId = defaultCurrencyLineItem.collect { it.lineItemId }

        and: "Line items are fetched by PBS"
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is requested"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Only line item with the default currency is ready to be served and was sent to bidder"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe == defaultCurrencyLineItemId as Set
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value) ==
                defaultCurrencyLineItemId as Set
    }
}
