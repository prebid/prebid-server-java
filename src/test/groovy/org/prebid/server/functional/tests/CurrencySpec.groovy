package org.prebid.server.functional.tests

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion

import java.math.RoundingMode

import static org.prebid.server.functional.model.Currency.CAD
import static org.prebid.server.functional.model.Currency.CHF
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer

class CurrencySpec extends BaseSpec {

    private static final Currency DEFAULT_CURRENCY = USD
    private static final int PRICE_PRECISION = 3
    private static final Map<Currency, Map<Currency, BigDecimal>> DEFAULT_CURRENCY_RATES = [(USD): [(USD): 1,
                                                                                                    (EUR): 0.9249838127832763,
                                                                                                    (CHF): 0.9033391915641477,
                                                                                                    (JPY): 151.1886041994265,
                                                                                                    (CAD): 1.357136250115623],
                                                                                            (EUR): [(USD): 1.3429368029739777]]
    private static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer).tap {
        setCurrencyConversionRatesResponse(CurrencyConversionRatesResponse.getDefaultCurrencyConversionRatesResponse(DEFAULT_CURRENCY_RATES))
    }
    private static final PrebidServerService pbsService = pbsServiceFactory.getService(externalCurrencyConverterConfig)

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
        assert bidPrice == convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
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
        assert bidPrice == convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
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
        assert bidPrice == convertCurrency(bidderResponse.seatbid[0].bid[0].price, bidCurrency, requestCurrency)
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

    private static Map<String, String> getExternalCurrencyConverterConfig() {
        ["auction.ad-server-currency"                          : DEFAULT_CURRENCY as String,
         "currency-converter.external-rates.enabled"           : "true",
         "currency-converter.external-rates.url"               : "$networkServiceContainer.rootUri/currency".toString(),
         "currency-converter.external-rates.default-timeout-ms": "4000",
         "currency-converter.external-rates.refresh-period-ms" : "900000"]
    }

    private static BigDecimal convertCurrency(BigDecimal price, Currency fromCurrency, Currency toCurrency) {
        return (price * getConversionRate(fromCurrency, toCurrency)).setScale(PRICE_PRECISION, RoundingMode.HALF_EVEN)
    }

    private static BigDecimal getConversionRate(Currency fromCurrency, Currency toCurrency) {
        def conversionRate
        if (fromCurrency == toCurrency) {
            conversionRate = 1
        } else if (toCurrency in DEFAULT_CURRENCY_RATES?[fromCurrency]) {
            conversionRate = DEFAULT_CURRENCY_RATES[fromCurrency][toCurrency]
        } else if (fromCurrency in DEFAULT_CURRENCY_RATES?[toCurrency]) {
            conversionRate = 1 / DEFAULT_CURRENCY_RATES[toCurrency][fromCurrency]
        } else {
            conversionRate = getCrossConversionRate(fromCurrency, toCurrency)
        }
        conversionRate
    }

    private static BigDecimal getCrossConversionRate(Currency fromCurrency, Currency toCurrency) {
        for (Map<Currency, BigDecimal> rates : DEFAULT_CURRENCY_RATES.values()) {
            def fromRate = rates?[fromCurrency]
            def toRate = rates?[toCurrency]

            if (fromRate && toRate) {
                return toRate / fromRate
            }
        }

        null
    }
}
