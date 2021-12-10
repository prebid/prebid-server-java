package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.pricefloors.Currency
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.pricefloors.Currency.EUR
import static org.prebid.server.functional.model.pricefloors.Currency.USD

class PriceFloorsCurrencySpec extends PriceFloorsBaseSpec {

    @PendingFeature
    def "PBS should update bidFloor, bidFloorCur for signalling when request.cur is specified"() {
        given: "Default BidRequest with cur"
        def floorValue = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorValue
            imp[0].bidFloorCur = EUR
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should bidFloorCur should correspond request currency"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            imp[0].bidFloor == bidRequest.imp[0].bidFloor * getCurrencyRate(imp[0].bidFloorCur, bidRequest.cur[0])
            imp[0].bidFloorCur == bidRequest.cur[0]
        }
    }

    @PendingFeature
    def "PBS should make FP enforcement with currency conversion when request.cur and bidFloorCur are different"() {
        given: "Default BidRequest with cur"
        def floorValue = randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorValue
            imp[0].bidFloorCur = EUR
        }

        and: "Account in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = EUR
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = floorValue
            seatbid.first().bid.last().price = floorValue - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should suppress bids lower than floorRuleValue"
        assert response.seatbid.first().bid.collect { it.price } == [floorValue]
    }

    private BigDecimal getCurrencyRate(Currency currencyFrom, Currency currencyTo) {
        def response = defaultPbsService.sendCurrencyRatesRequest()
        response.rates[currencyFrom][currencyTo]
    }
}
