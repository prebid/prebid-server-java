package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import org.prebid.server.functional.util.CurrencyUtil

import static org.prebid.server.functional.model.Currency.CAD
import static org.prebid.server.functional.model.Currency.CHF
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.CurrencyUtil.DEFAULT_CURRENCY

class CurrencySpec extends BaseSpec {

    private static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer)
    private static PrebidServerService pbsService

    def setupSpec() {
        currencyConversion.setCurrencyConversionRatesResponse()
        pbsService = pbsServiceFactory.getService(PbsConfig.currencyConverterConfig)
    }

    def "PBS should return currency rates"() {
        when: "PBS processes bidders params request"
        def response = pbsService.withWarmup().sendCurrencyRatesRequest()

        then: "Response should contain bidders params"
        assert response.rates?.size() > 0
    }

    def "PBS should use default server currency if not specified in the request"() {
        given: "Default BidRequest without currency"
        def bidRequest = BidRequest.defaultBidRequest.tap { cur = null }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Auction response should contain default currency"
        assert bidResponse.cur == DEFAULT_CURRENCY

        and: "Bidder request should contain default currency"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [DEFAULT_CURRENCY]
    }

    def "PBS should treat bids without currency as in default server currency"() {
        given: "Default BidRequest without currency"
        def bidRequest = BidRequest.defaultBidRequest.tap { cur = null }

        and: "Bid without currency"
        def bidderResponse = BidResponse.getDefaultBidResponse(bidRequest).tap { cur = null }

        when: "PBS processes auction request"
        bidder.setResponse(bidRequest.id, bidderResponse)
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Auction response should contain default currency"
        assert bidResponse.cur == DEFAULT_CURRENCY
        assert bidResponse.seatbid[0].bid[0].price == bidderResponse.seatbid[0].bid[0].price
    }

    def "PBS should convert #bidCurrency bid currency to #requestCurrency BidRequest currency"() {
        given: "Default BidRequest with #requestCurrency currency"
        def bidRequest = BidRequest.defaultBidRequest.tap { cur = [requestCurrency] }

        and: "Default Bid with a #bidCurrency currency"
        def bidderResponse = BidResponse.getDefaultBidResponse(bidRequest).tap { cur = bidCurrency }

        when: "PBS processes auction request"
        bidder.setResponse(bidRequest.id, bidderResponse)
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Auction response should contain bid in #requestCurrency currency"
        assert bidResponse.cur == requestCurrency
        def bidPrice = bidResponse.seatbid[0].bid[0].price
        assert bidPrice == CurrencyUtil.convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
        assert bidResponse.seatbid[0].bid[0].ext.origbidcpm == bidderResponse.seatbid[0].bid[0].price
        assert bidResponse.seatbid[0].bid[0].ext.origbidcur == bidCurrency

        where:
        requestCurrency || bidCurrency
        USD             || EUR
        EUR             || USD
    }

    def "PBS should use reverse currency conversion when direct conversion is not available"() {
        given: "Default BidRequest with #requestCurrency currency"
        def bidRequest = BidRequest.defaultBidRequest.tap { cur = [requestCurrency] }

        and: "Default Bid with a #bidCurrency currency"
        def bidderResponse = BidResponse.getDefaultBidResponse(bidRequest).tap { cur = bidCurrency }

        when: "PBS processes auction request"
        bidder.setResponse(bidRequest.id, bidderResponse)
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Auction response should contain bid in #requestCurrency currency"
        assert bidResponse.cur == requestCurrency
        def bidPrice = bidResponse.seatbid[0].bid[0].price
        assert bidPrice == CurrencyUtil.convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
        assert bidResponse.seatbid[0].bid[0].ext.origbidcpm == bidderResponse.seatbid[0].bid[0].price
        assert bidResponse.seatbid[0].bid[0].ext.origbidcur == bidCurrency

        where:
        requestCurrency || bidCurrency
        USD             || JPY
        JPY             || USD
    }

    def "PBS should use cross currency conversion when direct, reverse and intermediate conversion is not available"() {
        given: "Default BidRequest with #requestCurrency currency"
        def bidRequest = BidRequest.defaultBidRequest.tap { cur = [requestCurrency] }

        and: "Default Bid with a #bidCurrency currency"
        def bidderResponse = BidResponse.getDefaultBidResponse(bidRequest).tap { cur = bidCurrency }
        bidder.setResponse(bidRequest.id, bidderResponse)

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Auction response should contain bid in #requestCurrency currency"
        assert bidResponse.cur == requestCurrency
        def bidPrice = bidResponse.seatbid[0].bid[0].price
        assert bidPrice == CurrencyUtil.convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
        assert bidResponse.seatbid[0].bid[0].ext.origbidcpm == bidderResponse.seatbid[0].bid[0].price
        assert bidResponse.seatbid[0].bid[0].ext.origbidcur == bidCurrency

        where:
        requestCurrency || bidCurrency
        CHF             || JPY
        JPY             || CHF
        CAD             || JPY
        JPY             || CAD
        EUR             || CHF
        CHF             || EUR
    }

    def "PBS should emit warning when request contain more that one currency"() {
        given: "Default BidRequest with currencies"
        def currencies = [EUR, USD]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = currencies
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain first requested currency"
        assert bidResponse.cur == currencies[0]

        and: "Bidder request should contain requested currencies"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == currencies

        and: "Bid response should contain warnings"
        assert bidResponse.ext.warnings[GENERIC]?.message == ["a single currency (${currencies[0]}) has been chosen for the request. " +
                "ORTB 2.6 requires that all responses are in the same currency." as String]
    }

    def "PBS shouldn't emit warning when request contain one currency"() {
        given: "Default BidRequest with currency"
        def currency = [USD]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = currency
        }

        when: "PBS processes auction request"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain first requested currency"
        assert bidResponse.cur == currency[0]

        and: "Bidder request should contain requested currency"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == currency

        and: "Bid response shouldn't contain warnings"
        assert !bidResponse.ext.warnings
    }
}
