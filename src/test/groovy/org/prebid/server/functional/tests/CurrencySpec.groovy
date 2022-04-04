package org.prebid.server.functional.tests

import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion

import java.math.RoundingMode

import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class CurrencySpec extends BaseSpec {

    private static final String DEFAULT_CURRENCY = "USD"
    private static final int PRICE_PRECISION = 3
    private static final Map<String, Map<String, BigDecimal>> DEFAULT_CURRENCY_RATES = ["USD": ["EUR": 0.8872327211427558,
                                                                                                "JPY": 114.12],
                                                                                        "EUR": ["USD": 1.3429368029739777]]
    private static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer, mapper).tap {
        setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse.getDefaultCurrencyConversionRatesResponse(DEFAULT_CURRENCY_RATES))
    }
    private static final PrebidServerService pbsService = pbsServiceFactory.getService(externalCurrencyConverterConfig)

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
        assert bidPrice == convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
        assert bidResponse.seatbid[0].bid[0].ext.origbidcpm == bidderResponse.seatbid[0].bid[0].price
        assert bidResponse.seatbid[0].bid[0].ext.origbidcur == bidCurrency

        where:
        requestCurrency || bidCurrency
        "USD"           || "EUR"
        "EUR"           || "USD"
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
        assert bidPrice == convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
        assert bidResponse.seatbid[0].bid[0].ext.origbidcpm == bidderResponse.seatbid[0].bid[0].price
        assert bidResponse.seatbid[0].bid[0].ext.origbidcur == bidCurrency

        where:
        requestCurrency || bidCurrency
        "USD"           || "JPY"
        "JPY"           || "USD"
    }

    private static Map<String, String> getExternalCurrencyConverterConfig() {
        ["auction.ad-server-currency"                          : DEFAULT_CURRENCY,
         "currency-converter.external-rates.enabled"           : "true",
         "currency-converter.external-rates.url"               : "$networkServiceContainer.rootUri/currency".toString(),
         "currency-converter.external-rates.default-timeout-ms": "4000",
         "currency-converter.external-rates.refresh-period-ms" : "900000"]
    }

    private static BigDecimal convertCurrency(BigDecimal price, String fromCurrency, String toCurrency) {
        return (price * getConversionRate(fromCurrency, toCurrency)).setScale(PRICE_PRECISION, RoundingMode.HALF_EVEN)
    }

    private static BigDecimal getConversionRate(String fromCurrency, String toCurrency) {
        def conversionRate
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            conversionRate = 1
        } else if (fromCurrency in DEFAULT_CURRENCY_RATES) {
            conversionRate = DEFAULT_CURRENCY_RATES[fromCurrency][toCurrency]
        } else {
            conversionRate = 1 / DEFAULT_CURRENCY_RATES[toCurrency][fromCurrency]
        }
        conversionRate
    }
}
