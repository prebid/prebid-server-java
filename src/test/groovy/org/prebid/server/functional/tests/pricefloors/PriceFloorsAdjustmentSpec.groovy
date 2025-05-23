package org.prebid.server.functional.tests.pricefloors

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.pricefloors.PriceFloorField
import org.prebid.server.functional.model.pricefloors.Rule
import org.prebid.server.functional.model.request.auction.AdjustmentRule
import org.prebid.server.functional.model.request.auction.AdjustmentType
import org.prebid.server.functional.model.request.auction.Banner
import org.prebid.server.functional.model.request.auction.BidAdjustment
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentRule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidMediaType
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.AdjustmentType.CPM
import static org.prebid.server.functional.model.request.auction.AdjustmentType.MULTIPLIER
import static org.prebid.server.functional.model.request.auction.AdjustmentType.STATIC
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.ANY
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.AUDIO
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.BANNER
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.NATIVE
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.UNKNOWN
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO_IN_STREAM
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO_OUT_STREAM
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes.IN_STREAM as IN_PLACEMENT_STREAM
import static org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype.IN_STREAM as IN_PLCMT_STREAM
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class PriceFloorsAdjustmentSpec extends PriceFloorsBaseSpec {

    private static final Integer MIN_ADJUST_VALUE = 0
    private static final Integer MAX_MULTIPLIER_ADJUST_VALUE = 99
    private static final VideoPlacementSubtypes RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM = PBSUtils.getRandomEnum(VideoPlacementSubtypes, [IN_PLACEMENT_STREAM])
    private static final VideoPlcmtSubtype RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM = PBSUtils.getRandomEnum(VideoPlcmtSubtype, [IN_PLCMT_STREAM])
    private static final Integer MAX_CPM_ADJUST_VALUE = 5
    private static final Integer MAX_STATIC_ADJUST_VALUE = Integer.MAX_VALUE
    private static final String WILDCARD = '*'

    private static final Map config = CURRENCY_CONVERTER_CONFIG +
            FLOORS_CONFIG +
            GENERIC_ALIAS_CONFIG +
            ["adapters.openx.enabled" : "true",
             "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()] +
            ["adapter-defaults.ortb.multiformat-supported": "true"]
    private static final PrebidServerService pbsService = pbsServiceFactory.getService(config)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(config)
    }

    def "PBS should reverse imp.floors for matching bidder when request has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = impPrice
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default bid response"
        def originalPrice = PBSUtils.getRandomDecimal(impPrice)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain reversed floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, ruleValue as BigDecimal, adjustmentType)]

        where:
        adjustmentType | ruleValue                                                              | mediaType        | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)
    }

    def "PBS should left original bidderRequest with null floors when request has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = null
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [null]

        where:
        adjustmentType | ruleValue                                                              | mediaType        | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)
    }

    def "PBS should reverse imp.floors for matching bidder when request with multiple imps has specific bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def firstImpPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def secondImpPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = firstImpPrice
        bidRequest.imp.first.bidFloorCur = currency
        def secondImp = Imp.defaultImpression.tap {
            bidFloor = secondImpPrice
            bidFloorCur = currency
        }
        bidRequest.imp.add(secondImp)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.dealid = PBSUtils.randomString
        } as BidResponse
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted for big with dealId"
        assert response.seatbid.first.bid.findAll() { it.impid == bidRequest.imp.first.id }.price == [getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)]

        and: "Price shouldn't be updated for bid with different dealId"
        assert response.seatbid.first.bid.findAll() { it.impid == bidRequest.imp.last.id }.price == [bidResponse.seatbid.first.bid.last.price]

        and: "Response currency should stay the same"
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        assert response.seatbid.first.bid.ext.origbidcpm.sort() == bidResponse.seatbid.first.bid.price.sort()
        assert response.seatbid.first.bid.ext.first.origbidcur == bidResponse.cur
        assert response.seatbid.first.bid.ext.last.origbidcur == bidResponse.cur

        and: "Bidder request should contain reversed imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency, currency]
        assert bidderRequest.imp.bidFloor.sort() == [getReverseAdjustedPrice(firstImpPrice, ruleValue as BigDecimal, adjustmentType), secondImpPrice].sort()

        where:
        adjustmentType | ruleValue                                                              | mediaType | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE    | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE    | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE    | getBidRequestWithFloors(MediaType.NATIVE)
    }

    def "PBS shouldn't reverse imp.floors for matching bidder with specific dealId when request with multiple imps has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def dealId = PBSUtils.randomString
        def currency = USD
        def firstImpPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def secondImpPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(dealId): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = firstImpPrice
        bidRequest.imp.first.bidFloorCur = currency
        def secondImp = Imp.defaultImpression.tap {
            bidFloor = secondImpPrice
            bidFloorCur = currency
        }
        bidRequest.imp.add(secondImp)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.dealid = dealId
        } as BidResponse
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted for big with dealId"
        assert response.seatbid.first.bid.findAll() { it.dealid == dealId }.price == [getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)]

        and: "Price shouldn't be updated for bid with different dealId"
        assert response.seatbid.first.bid.findAll() { it.dealid != dealId }.price == bidResponse.seatbid.first.bid.findAll() { it.dealid != dealId }.price

        and: "Response currency should stay the same"
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        assert response.seatbid.first.bid.ext.origbidcpm.sort() == bidResponse.seatbid.first.bid.price.sort()
        assert response.seatbid.first.bid.ext.first.origbidcur == bidResponse.cur
        assert response.seatbid.first.bid.ext.last.origbidcur == bidResponse.cur

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency, currency]
        assert bidderRequest.imp.bidFloor.sort() == [firstImpPrice, secondImpPrice].sort()

        where:
        adjustmentType | ruleValue                                                              | mediaType        | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)
    }

    def "PBS should reverse imp.floors for matching bidder when account config has bidAdjustments"() {
        given: "BidRequest with floors"
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def currency = USD
        bidRequest.imp.first.bidFloor = impPrice
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        } as BidResponse
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB with bidAdjustments"
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        def accountConfig = new AccountAuctionConfig(bidAdjustments: BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain reversed imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, ruleValue as BigDecimal, adjustmentType)]

        where:
        adjustmentType | ruleValue                                                              | mediaType        | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)
    }

    def "PBS should prioritize BidAdjustmentRule from request when account and request config bidAdjustments conflict"() {
        given: "BidRequest with floors"
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def currency = USD
        bidRequest.imp.first.bidFloor = impPrice
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default BidRequest with ext.prebid.bidAdjustments"
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]

        and: "Account in the DB with bidAdjustments"
        def accountRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        def accountConfig = new AccountAuctionConfig(bidAdjustments: BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, accountRule))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        } as BidResponse
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted according to request config"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, ruleValue as BigDecimal, adjustmentType)]

        where:
        adjustmentType | ruleValue                                                              | mediaType        | bidRequest
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | getBidRequestWithFloors(MediaType.BANNER)

        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | ANY              | getBidRequestWithFloors(MediaType.BANNER)
    }

    def "PBS should prioritize exact imp.floors reverser for matching bidder when request has exact and general bidAdjustment"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def exactRulePrice = PBSUtils.randomDecimal
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def currency = USD
        def exactRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)]])
        def generalRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomPrice, currency: currency)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = new BidAdjustment(mediaType: [(BANNER): exactRule, (ANY): generalRule])
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted according to exact rule"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, exactRulePrice, STATIC)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, exactRulePrice, STATIC)]
    }

    def "PBS should adjust bid price for matching bidder in provided order when bidAdjustments have multiple matching rules"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def firstRule = new AdjustmentRule(adjustmentType: firstRuleType, value: firstRuleValue, currency: currency)
        def secondRule = new AdjustmentRule(adjustmentType: secondRuleType, value: secondRuleValue, currency: currency)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [firstRule, secondRule]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        def rawAdjustedBidPrice = getAdjustedPrice(originalPrice, firstRule.value as BigDecimal, firstRule.adjustmentType)
        def adjustedBidPrice = getAdjustedPrice(rawAdjustedBidPrice, secondRule.value as BigDecimal, secondRule.adjustmentType)
        assert response.seatbid.first.bid.first.price == adjustedBidPrice
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [applyReverseAdjustments(impPrice, [firstRule, secondRule])]

        where:
        firstRuleType | secondRuleType | firstRuleValue                                                         | secondRuleValue
        MULTIPLIER    | CPM            | PBSUtils.randomPrice                                                   | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)
        MULTIPLIER    | STATIC         | PBSUtils.randomPrice                                                   | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)
        MULTIPLIER    | MULTIPLIER     | PBSUtils.randomPrice                                                   | PBSUtils.randomPrice
        CPM           | CPM            | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, 1)                           | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, 1)
        CPM           | STATIC         | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)
        CPM           | MULTIPLIER     | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | PBSUtils.randomPrice
        STATIC        | CPM            | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | PBSUtils.randomPrice
        STATIC        | STATIC         | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)
        STATIC        | MULTIPLIER     | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE) | PBSUtils.randomPrice
    }

    def "PBS should prioritize revert with lower resulting value for matching bidder when request has multiple media types"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def impPrice = PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)
        def currency = USD
        def firstRule = new BidAdjustmentRule(openx: [(WILDCARD): [new AdjustmentRule(adjustmentType: MULTIPLIER, value: firstRulePrice, currency: currency)]])
        def secondRule = new BidAdjustmentRule(openx: [(WILDCARD): [new AdjustmentRule(adjustmentType: MULTIPLIER, value: secondRulePrice, currency: currency)]])
        def bidRequest = getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM).tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            imp[0].ext.prebid.bidder.generic = null
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            imp.first.banner = Banner.getDefaultBanner()
            imp.first.nativeObj = Native.getDefaultNative()
            ext.prebid.bidAdjustments = new BidAdjustment(mediaType: [(primaryType): firstRule, (BANNER): secondRule])
            ext.prebid.multibid = [new MultiBid(bidder: OPENX, maxBids: 3)]
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid = Bid.getDefaultMultyTypesBids(bidRequest.imp.first) {
                price = originalPrice
                ext = new BidExt()
            }

        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted according to first matched rule"
        getMediaTypedBids(response, BidMediaType.from(primaryType)).price == [getAdjustedPrice(originalPrice, firstRulePrice, MULTIPLIER)]
        getMediaTypedBids(response, BidMediaType.BANNER).price == [getAdjustedPrice(originalPrice, secondRulePrice, MULTIPLIER)]
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain revert imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, [firstRulePrice, secondRulePrice].max(), MULTIPLIER)]

        where:
        primaryType     | firstRulePrice                                                  | secondRulePrice
        VIDEO_IN_STREAM | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE) | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        NATIVE          | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE) | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)

        VIDEO_IN_STREAM | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)                   | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)
        NATIVE          | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)                   | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)
    }

    def "PBS should convert CPM currency before adjustment when it different from original response currency"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentRule = new AdjustmentRule(adjustmentType: CPM, value: PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE), currency: GBP)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def currency = EUR
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [EUR]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Get currency rates"
        def currencyRatesResponse = pbsService.sendCurrencyRatesRequest()

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        def convertedAdjustment = getPriceAfterCurrencyConversion(adjustmentRule.value, adjustmentRule.currency, bidResponse.cur, currencyRatesResponse)
        def adjustedBidPrice = getAdjustedPrice(originalPrice, convertedAdjustment, adjustmentRule.adjustmentType)
        assert response.seatbid.first.bid.first.price == getPriceAfterCurrencyConversion(adjustedBidPrice, bidResponse.cur, currency, currencyRatesResponse)

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        def convertedReverseAdjustment = getPriceAfterCurrencyConversion(adjustmentRule.value, adjustmentRule.currency, currency, currencyRatesResponse)
        def reversedAdjustBidPrice = getReverseAdjustedPrice(impPrice, convertedReverseAdjustment, adjustmentRule.adjustmentType)
        assert bidderRequest.imp.bidFloor == [reversedAdjustBidPrice]
    }

    def "PBS should change original currency when static bidAdjustments and original response have different currencies"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomDecimal, currency: GBP)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def currency = EUR
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
        }

        and: "Default bid response with JPY currency"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Get currency rates"
        def currencyRatesResponse = pbsService.sendCurrencyRatesRequest()

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted and converted to original request cur"
        assert response.seatbid.first.bid.first.price ==
                getPriceAfterCurrencyConversion(adjustmentRule.value, adjustmentRule.currency, currency, currencyRatesResponse)
        assert response.cur == bidRequest.cur.first

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]
    }

    def "PBS should apply bidAdjustments revert for imp.floors after bidAdjustmentFactors when both are present"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def bidAdjustmentFactorsPrice = PBSUtils.randomPrice
        def adjustmentRule = new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentValue, currency: currency)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactorsPrice])
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        def bidAdjustedPrice = originalPrice * bidAdjustmentFactorsPrice
        assert !response.ext.warnings
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(bidAdjustedPrice, adjustmentRule.value, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        def reversedBidPrice = impPrice / bidAdjustmentFactorsPrice
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(reversedBidPrice, adjustmentRule.value, adjustmentType)]

        where:
        adjustmentType | impPrice                                                        | adjustmentValue
        MULTIPLIER     | PBSUtils.getRandomPrice()                                       | PBSUtils.getRandomPrice()
        CPM            | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)                   | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)
        STATIC         | PBSUtils.getRandomPrice(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE) | PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when request has invalid value bidAdjustments config"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = impPrice
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        } as BidResponse
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidAdjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        def errorMessage = "bid adjustment from request was invalid: the found rule [adjtype=${adjustmentType}, " +
                "value=${ruleValue}, currency=${currency}] in ${mediaType.value}.generic.* is invalid" as String
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message == [errorMessage]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "PBS log should contain error"
        def logs = pbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, errorMessage)

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType | ruleValue                       | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | ANY              | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | ANY              | getBidRequestWithFloors(MediaType.NATIVE)

        CPM            | MIN_ADJUST_VALUE - 1            | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        CPM            | MIN_ADJUST_VALUE - 1            | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        CPM            | MIN_ADJUST_VALUE - 1            | ANY              | getBidRequestWithFloors(MediaType.NATIVE)

        STATIC         | MIN_ADJUST_VALUE - 1            | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | MIN_ADJUST_VALUE - 1            | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | MIN_ADJUST_VALUE - 1            | ANY              | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | BANNER           | getBidRequestWithFloors(MediaType.BANNER)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | AUDIO            | getBidRequestWithFloors(MediaType.AUDIO)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | NATIVE           | getBidRequestWithFloors(MediaType.NATIVE)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | ANY              | getBidRequestWithFloors(MediaType.NATIVE)
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when request has different bidder name in bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(alias: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: PBSUtils.randomPrice, currency: currency)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidAdjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when cpm or static bidAdjustments doesn't have currency value"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def adjustmentPrice = PBSUtils.randomPrice.toDouble()
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidAdjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        def errorMessage = "bid adjustment from request was invalid: the found rule [adjtype=${adjustmentType}, " +
                "value=${adjustmentPrice}, currency=null] in banner.generic.* is invalid" as String
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message == [errorMessage]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "PBS log should contain error"
        def logs = pbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, errorMessage)

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [CPM, STATIC]
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when bidAdjustments have unknown mediatype"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.randomPrice
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(UNKNOWN, rule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidAdjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when bidAdjustments have unknown adjustmentType"() {
        given: "Start time"
        def startTime = Instant.now()

        and: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def adjustmentPrice = PBSUtils.randomPrice.toDouble()
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: AdjustmentType.UNKNOWN, value: adjustmentPrice, currency: currency)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
        }

        and: "Default bid response"
        def originalPrice = impPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidAdjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        def errorMessage = "bid adjustment from request was invalid: the found rule [adjtype=UNKNOWN, " +
                "value=$adjustmentPrice, currency=$currency] in banner.generic.* is invalid" as String
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message == [errorMessage]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "PBS log should contain error"
        def logs = pbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, errorMessage)

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
    }

    def "PBS shouldn't reverse imp.floors for matching bidder when multiplier bidAdjustments doesn't have currency value"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def adjustmentPrice = PBSUtils.randomPrice
        def impPrice = PBSUtils.getRandomPrice(MAX_CPM_ADJUST_VALUE)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: MULTIPLIER, value: adjustmentPrice, currency: null)]])
        def bidRequest = getBidRequestWithFloors(MediaType.BANNER).tap {
            cur = [currency]
            imp.first.tap {
                bidFloor = impPrice
                bidFloorCur = currency
            }
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomDecimal
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, adjustmentPrice, MULTIPLIER)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [getReverseAdjustedPrice(impPrice, adjustmentPrice, MULTIPLIER)]

        where:
        adjustmentType << [CPM, STATIC]
    }

    private static BidRequest getDefaultVideoRequestWithPlacement(VideoPlacementSubtypes videoPlacementSubtypes) {
        getBidRequestWithFloors(MediaType.VIDEO).tap {
            imp.first.video.tap {
                placement = videoPlacementSubtypes
            }
        }
    }

    private static BidRequest getDefaultVideoRequestWithPlcmt(VideoPlcmtSubtype videoPlcmtSubtype) {
        getBidRequestWithFloors(MediaType.VIDEO).tap {
            imp.first.video.tap {
                plcmt = videoPlcmtSubtype
            }
        }
    }

    private static BidRequest getDefaultVideoRequestWithPlcmtAndPlacement(VideoPlcmtSubtype videoPlcmtSubtype,
                                                                          VideoPlacementSubtypes videoPlacementSubtypes) {
        getBidRequestWithFloors(MediaType.VIDEO).tap {
            imp.first.video.tap {
                plcmt = videoPlcmtSubtype
                placement = videoPlacementSubtypes
            }
        }
    }

    private static BigDecimal getReverseAdjustedPrice(BigDecimal originalPrice,
                                                      BigDecimal adjustedValue,
                                                      AdjustmentType adjustmentType) {
        switch (adjustmentType) {
            case MULTIPLIER:
                return PBSUtils.roundDecimal(originalPrice / adjustedValue, FLOOR_VALUE_PRECISION)
            case CPM:
                return PBSUtils.roundDecimal(originalPrice + adjustedValue, FLOOR_VALUE_PRECISION)
            case STATIC:
                return PBSUtils.roundDecimal(originalPrice, FLOOR_VALUE_PRECISION)
            default:
                return adjustedValue
        }
    }

    private static BigDecimal applyReverseAdjustments(BigDecimal originalPrice, List<AdjustmentRule> rules) {
        if (!rules || rules.any { it.adjustmentType == STATIC }) {
            return originalPrice
        }
        def result = originalPrice
        rules.reverseEach {
            result = getReverseAdjustedPrice(result, it.value, it.adjustmentType)
        }
        result
    }

    private static BigDecimal getAdjustedPrice(BigDecimal originalPrice,
                                               BigDecimal adjustedValue,
                                               AdjustmentType adjustmentType) {
        switch (adjustmentType) {
            case MULTIPLIER:
                return PBSUtils.roundDecimal(originalPrice * adjustedValue, FLOOR_VALUE_PRECISION)
            case CPM:
                return PBSUtils.roundDecimal(originalPrice - adjustedValue, FLOOR_VALUE_PRECISION)
            case STATIC:
                return adjustedValue
            default:
                return originalPrice
        }
    }

    private static BidRequest getBidRequestWithFloors(MediaType type,
                                                      DistributionChannel channel = SITE) {
        def floors = ExtPrebidFloors.extPrebidFloors.tap {
            data.modelGroups.first.values = [(new Rule(channel: PBSUtils.randomString)
                    .getRule([PriceFloorField.CHANNEL])): PBSUtils.randomFloorValue]
        }
        BidRequest.getDefaultBidRequest(type, channel).tap {
            ext.prebid.floors = floors
        }
    }
}
