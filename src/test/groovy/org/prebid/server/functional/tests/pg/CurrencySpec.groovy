package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.lineitem.LineItem
import org.prebid.server.functional.model.deals.lineitem.Price
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class CurrencySpec extends BasePgSpec {

    private static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer, mapper).tap {
        setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse.defaultCurrencyConversionRatesResponse)
    }

    private static final Map<String, String> pgCurrencyConverterPbsConfig = externalCurrencyConverterConfig + pgConfig.properties
    private static final PrebidServerService pgCurrencyConverterPbsService = pbsServiceFactory.getService(pgCurrencyConverterPbsConfig)

    @Shared
    BidRequest bidRequest

    def setup() {
        bidRequest = BidRequest.defaultBidRequest
        bidder.setResponse(bidRequest.id, BidResponse.getDefaultBidResponse(bidRequest))
        pgCurrencyConverterPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }

    def "PBS should convert non-default line item currency to the default one during the bidder auction"() {
        given: "Planner Mock line items with the same CPM but different currencies"
        def accountId = bidRequest.site.publisher.id
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
        pgCurrencyConverterPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is requested"
        def auctionResponse = pgCurrencyConverterPbsService.sendAuctionRequest(bidRequest)

        then: "All line items are ready to be served"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe?.size() == plansResponse.lineItems.size()

        and: "Line Item with EUR defaultCurrency was sent to bidder as EUR defaultCurrency rate > than USD"
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value)?.sort() ==
                nonDefaultCurrencyLineItemIds.sort()
    }

    def "PBS should invalidate line item with an unknown for the conversion rate currency"() {
        given: "Planner Mock line items with a default currency and unknown currency"
        def defaultCurrency = Price.defaultPrice.currency
        def unknownCurrency = "UAH"
        def defaultCurrencyLineItem = [LineItem.getDefaultLineItem(bidRequest.site.publisher.id).tap { price = new Price(cpm: 1, currency: defaultCurrency) }]
        def unknownCurrencyLineItem = [LineItem.getDefaultLineItem(bidRequest.site.publisher.id).tap { price = new Price(cpm: 1, currency: unknownCurrency) }]
        def lineItems = defaultCurrencyLineItem + unknownCurrencyLineItem
        def plansResponse = new PlansResponse(lineItems: lineItems)
        generalPlanner.initPlansResponse(plansResponse)
        def defaultCurrencyLineItemId = defaultCurrencyLineItem.collect { it.lineItemId }

        and: "Line items are fetched by PBS"
        pgCurrencyConverterPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)

        when: "Auction is requested"
        def auctionResponse = pgCurrencyConverterPbsService.sendAuctionRequest(bidRequest)

        then: "Only line item with the default currency is ready to be served and was sent to bidder"
        assert auctionResponse.ext?.debug?.pgmetrics?.readyToServe == defaultCurrencyLineItemId as Set
        assert auctionResponse.ext?.debug?.pgmetrics?.sentToBidder?.get(GENERIC.value) ==
                defaultCurrencyLineItemId as Set
    }

    private static Map<String, String> getExternalCurrencyConverterConfig() {
        ["currency-converter.external-rates.enabled"           : "true",
         "currency-converter.external-rates.url"               : "$networkServiceContainer.rootUri/currency".toString(),
         "currency-converter.external-rates.default-timeout-ms": "4000",
         "currency-converter.external-rates.refresh-period-ms" : "900000"
        ]
    }
}
