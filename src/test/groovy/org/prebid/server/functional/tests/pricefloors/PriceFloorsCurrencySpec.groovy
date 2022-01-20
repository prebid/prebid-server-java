package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import spock.lang.PendingFeature

import java.math.RoundingMode

import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.USD

class PriceFloorsCurrencySpec extends PriceFloorsBaseSpec {

    private static final int FLOOR_VALUE_PRECISION = 4

    @PendingFeature
    def "PBS should update bidFloor, bidFloorCur for signalling when request.cur is specified"() {
        given: "Default BidRequest with cur"
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain bidFloor, bidFloorCur from floors provider"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0]?.bidFloor == floorValue
            imp[0]?.bidFloorCur == floorsResponse.data.modelGroups[0].currency
        }
    }

    @PendingFeature
    def "PBS should make FP enforcement with currency conversion when request.cur and floor currency are different"() {
        given: "Default BidRequest with cur"
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the request.cur"
        def floorValue = randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids: price < floorMin, price = floorMin"
        def convertedMinFloorValue = floorValue *
                getCurrencyRate(floorsResponse.data.modelGroups[0].currency, bidRequest.cur[0])
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = EUR
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = convertedMinFloorValue
            seatbid.first().bid.last().price = convertedMinFloorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should suppress bids lower than floorRuleValue"
        assert response.seatbid?.first()?.bid?.collect { it.price } == [getRoundedFloorValue(convertedMinFloorValue)]
        assert response.cur == bidRequest.cur[0]
    }

    @PendingFeature
    def "PBS should update bidFloor, bidFloorCur for signalling when floorMinCur is defined in request"() {
        given: "BidRequest with floorMinCur"
        def floorMin = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorMin
            imp[0].bidFloorCur = EUR
            ext.prebid.floors.floorMin = floorMin
            ext.prebid.floors.floorMinCur = USD
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the floorMinCur, floorValur lower then floorMin"
        def floorProviderCur = EUR
        def convertedMinFloorValue = floorMin *
                getCurrencyRate(bidRequest.ext.prebid.floors.floorMinCur, floorProviderCur)

        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = randomFloorValue
            data.modelGroups[0].values = [(rule): convertedMinFloorValue - 0.1]
            data.modelGroups[0].currency = floorProviderCur
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond floorMin"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0]?.bidFloor == getRoundedFloorValue(convertedMinFloorValue)
            imp[0]?.bidFloorCur == floorProviderCur
        }
    }

    @PendingFeature
    def "PBS should not update bidFloor, bidFloorCur for signalling when currency conversion is not available"() {
        given: "Pbs config with disabled conversion"
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["currency-converter.external-rates.enabled": "false"])

        and: "BidRequest with floorMinCur"
        def floorMin = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorMin
            imp[0].bidFloorCur = USD
            ext.prebid.floors.floorMin = floorMin
            ext.prebid.floors.floorMinCur = USD
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the floorMinCur"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): randomFloorValue]
            data.modelGroups[0].currency = EUR
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999] //TODO what code?
        assert response.ext?.warnings[ErrorType.PREBID]*.message == ["placeholder"]

        and: "Bidder request should contain bidFloor, bidFloorCur from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0]?.bidFloor == floorMin
            imp[0]?.bidFloorCur == bidRequest.ext.prebid.floors.floorMinCur
            !imp[0]?.ext?.prebid?.floors
        }
    }

    private BigDecimal getCurrencyRate(Currency currencyFrom, Currency currencyTo) {
        def response = defaultPbsService.sendCurrencyRatesRequest()
        response.rates[currencyFrom][currencyTo]
    }

    private BigDecimal getRoundedFloorValue(BigDecimal floorValue) {
        floorValue.setScale(FLOOR_VALUE_PRECISION, RoundingMode.HALF_EVEN)
    }
}
