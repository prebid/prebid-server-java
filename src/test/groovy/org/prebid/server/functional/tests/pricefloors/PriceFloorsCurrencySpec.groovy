package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.mock.services.floorsprovider.PriceFloorRules
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.Currency.BOGUS
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.request.auction.FetchStatus.NONE
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.request.auction.Location.FETCH
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class PriceFloorsCurrencySpec extends PriceFloorsBaseSpec {

    private static final String GENERAL_ERROR_METRIC = "price-floors.general.err"

    def "PBS should update bidFloor, bidFloorCur for signalling when request.cur is specified"() {
        given: "Default BidRequest with cur"
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain bidFloor, bidFloorCur from floors provider"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorValue
            imp[0].bidFloorCur == floorsResponse.data.modelGroups[0].currency
        }
    }

    def "PBS should make FP enforcement with currency conversion when request.cur and floor currency are different"() {
        given: "Default BidRequest with cur"
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the request.cur"
        def floorValue = PBSUtils.randomFloorValue
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = USD
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        and: "Bid response with 2 bids: price < floorMin, price = floorMin"
        def convertedMinFloorValue = getPriceAfterCurrencyConversion(floorValue,
                floorsResponse.data.modelGroups[0].currency, bidRequest.cur[0])
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
        assert response.seatbid?.first()?.bid?.collect { it.price } == [convertedMinFloorValue]
        assert response.cur == bidRequest.cur[0]
    }

    def "PBS should update bidFloor, bidFloorCur for signalling when floorMinCur is defined in request"() {
        given: "BidRequest with floorMinCur"
        def floorMin = PBSUtils.randomFloorValue
        def requestFloorMinCur = USD
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorMin
            imp[0].bidFloorCur = EUR
            ext.prebid.floors.floorMin = floorMin
            ext.prebid.floors.floorMinCur = requestFloorMinCur
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the floorMinCur, floorValur lower then floorMin"
        def floorProviderCur = EUR
        def convertedMinFloorValue = getPriceAfterCurrencyConversion(floorMin,
                bidRequest.ext.prebid.floors.floorMinCur, floorProviderCur)

        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): convertedMinFloorValue - 0.1]
            data.modelGroups[0].currency = floorProviderCur
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request bidFloor should correspond floorMin"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == getRoundedFloorValue(convertedMinFloorValue)
            imp[0].bidFloorCur == floorProviderCur
            ext?.prebid?.floors?.floorMin == floorMin
            ext?.prebid?.floors?.floorMinCur == requestFloorMinCur
        }
    }

    def "PBS should not update bidFloor, bidFloorCur for signalling when currency conversion is not available"() {
        given: "Pbs config with disabled conversion"
        def pbsService = pbsServiceFactory.getService(floorsConfig +
                ["currency-converter.external-rates.enabled": "false"])

        and: "BidRequest with floorMinCur"
        def requestFloorCur = USD
        def floorMin = PBSUtils.randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorMin
            imp[0].bidFloorCur = requestFloorCur
            ext.prebid.floors.floorMin = floorMin
            ext.prebid.floors.floorMinCur = requestFloorCur
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the floorMinCur"
        def floorsProviderCur = EUR
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): PBSUtils.randomFloorValue]
            data.modelGroups[0].currency = floorsProviderCur
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(pbsService, bidRequest)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log an error"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [999]
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["Unable to convert from currency $bidRequest.ext.prebid.floors.floorMinCur to desired ad server" +
                         " currency ${floorsResponse.data.modelGroups[0].currency}" as String]

        and: "PBS should log a warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Error occurred while resolving floor for imp: ${bidRequest.imp[0].id}, cause: Unable "+
                "to convert from currency $requestFloorCur to desired ad server currency $floorsProviderCur" as String]

        and: "Metric #GENERAL_ERROR_METRIC should be update"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[GENERAL_ERROR_METRIC] == 1

        and: "Bidder request should contain bidFloor, bidFloorCur from request"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorMin
            imp[0].bidFloorCur == bidRequest.ext.prebid.floors.floorMinCur
            !imp[0].ext?.prebid?.floors

            ext?.prebid?.floors?.location == FETCH
            ext?.prebid?.floors?.fetchStatus == SUCCESS
        }
    }

    def "PBS should update bidFloor, bidFloorCur for signalling when floors becomes from request"() {
        given: "Default BidRequest with cur"
        def floorValue = PBSUtils.randomFloorValue
        def floorCur = USD
        def bidRequest = bidRequestClosure(floorValue, floorCur) as BidRequest

        and: "Account with disabled fetch in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id).tap {
            config.auction.priceFloors.fetch.enabled = false
        }
        accountDao.save(account)

        when: "PBS processes auction request"
        floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain bidFloor, bidFloorCur from request"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorValue
            imp[0].bidFloorCur == floorCur
            ext?.prebid?.floors?.fetchStatus == NONE
        }

        where:
        bidRequestClosure << [{ BigDecimal floorValueObj, Currency floorCurObj -> bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = PBSUtils.randomFloorValue
            imp[0].bidFloorCur = EUR
            ext.prebid.floors.floorMin = floorValueObj
            ext.prebid.floors.data.modelGroups[0].values = [(rule): floorValueObj]
            ext.prebid.floors.data.modelGroups[0].currency = floorCurObj
        } },
           { BigDecimal floorValueObj, Currency floorCurObj -> bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorValueObj
            imp[0].bidFloorCur = floorCurObj
            ext.prebid.floors = null
        } }]
    }

    def "PBS should make FP enforcement with currency conversion when request.cur, floor cur, bidResponse cur are different"() {
        given: "Default BidRequest with cur"
        def requestCur = EUR
        def bidRequest = bidRequestWithFloors.tap {
            cur = [requestCur]
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the request.cur"
        def floorValue = PBSUtils.randomFloorValue
        def floorCur = USD
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            floorMin = floorValue
            data.modelGroups[0].values = [(rule): floorValue]
            data.modelGroups[0].currency = floorCur
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        and: "Bid response with 2 bids: price < floorMin, price = floorMin"
        def bidResponseCur = GBP
        def convertedMinFloorValueGbp = getPriceAfterCurrencyConversion(floorValue, floorCur, bidResponseCur)
        def winBidPrice = convertedMinFloorValueGbp + 0.1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = bidResponseCur
            seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())
            seatbid.first().bid.first().price = winBidPrice
            seatbid.first().bid.last().price = convertedMinFloorValueGbp - 0.1
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain bidFloor, bidFloorCur from floors provider"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorValue
            imp[0].bidFloorCur == floorCur
        }

        and: "PBS should suppress bids lower than floorRuleValue"
        def convertedFloorValueEur = getPriceAfterCurrencyConversion(winBidPrice, bidResponseCur, requestCur)
        assert response.seatbid?.first()?.bid?.collect { it.price } == [convertedFloorValueEur]
        assert response.cur == bidRequest.cur[0]
    }

    def "PBS should update bidFloor, bidFloorCur for signalling when request.cur is specified UNKNOWN"() {
        given: "BidRequest with floorMinCur"
        def requestFloorCur = USD
        def floorMin = PBSUtils.randomFloorValue
        def bidRequest = bidRequestWithFloors.tap {
            cur = [EUR]
            imp[0].bidFloor = floorMin
            imp[0].bidFloorCur = requestFloorCur
            ext.prebid.floors.floorMin = floorMin
            ext.prebid.floors.floorMinCur = requestFloorCur
        }

        and: "Account with enabled fetch, fetch.url in the DB"
        def account = getAccountWithEnabledFetch(bidRequest.site.publisher.id)
        accountDao.save(account)

        and: "Set Floors Provider response with a currency different from the floorMinCur"
        def floorsProviderCur = BOGUS
        def floorsResponse = PriceFloorRules.priceFloorRules.tap {
            data.modelGroups[0].values = [(rule): PBSUtils.randomFloorValue]
            data.modelGroups[0].currency = floorsProviderCur
        }
        floorsProvider.setResponse(bidRequest.site.publisher.id, floorsResponse)

        and: "PBS fetch rules from floors provider"
        cacheFloorsProviderRules(bidRequest)

        when: "PBS processes auction request"
        def response = floorsPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should log a warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Error occurred while resolving floor for imp: ${bidRequest.imp[0].id}, cause: Unable "+
                "to convert from currency $requestFloorCur to desired ad server currency $floorsProviderCur" as String]

        and: "Metric #GENERAL_ERROR_METRIC should be update"
        def metrics = floorsPbsService.sendCollectedMetricsRequest()
        assert metrics[GENERAL_ERROR_METRIC] == 1

        and: "Bidder request should contain bidFloor, bidFloorCur from request"
        def bidderRequest = bidder.getBidderRequests(bidRequest.id).last()
        verifyAll(bidderRequest) {
            imp[0].bidFloor == floorMin
            imp[0].bidFloorCur == bidRequest.ext.prebid.floors.floorMinCur
            !imp[0].ext?.prebid?.floors
        }
    }
}
