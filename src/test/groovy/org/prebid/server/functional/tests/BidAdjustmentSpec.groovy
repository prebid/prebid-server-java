package org.prebid.server.functional.tests


import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AlternateBidderCodes
import org.prebid.server.functional.model.config.BidderConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.AdjustmentRule
import org.prebid.server.functional.model.request.auction.AdjustmentType
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.BidAdjustment
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentRule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import org.prebid.server.functional.util.CurrencyUtil
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.USD
import static org.prebid.server.functional.model.bidder.BidderName.ACUITYADS
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.auction.AdjustmentType.CPM
import static org.prebid.server.functional.model.request.auction.AdjustmentType.MULTIPLIER
import static org.prebid.server.functional.model.request.auction.AdjustmentType.STATIC
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.ANY
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.AUDIO
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.BANNER
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.NATIVE
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.UNKNOWN
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO_IN_STREAM
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO_OUT_STREAM
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes.IN_STREAM as IN_PLACEMENT_STREAM
import static org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype.IN_STREAM as IN_PLCMT_STREAM
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.PBSUtils.getRandomDecimal

class BidAdjustmentSpec extends BaseSpec {

    private static final String WILDCARD = '*'
    private static final BigDecimal MIN_ADJUST_VALUE = 0
    private static final BigDecimal MAX_MULTIPLIER_ADJUST_VALUE = 99
    private static final BigDecimal MAX_CPM_ADJUST_VALUE = Integer.MAX_VALUE
    private static final BigDecimal MAX_STATIC_ADJUST_VALUE = Integer.MAX_VALUE
    private static final int BID_ADJUST_PRECISION = 4
    private static final VideoPlacementSubtypes RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM = PBSUtils.getRandomEnum(VideoPlacementSubtypes, [IN_PLACEMENT_STREAM])
    private static final VideoPlcmtSubtype RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM = PBSUtils.getRandomEnum(VideoPlcmtSubtype, [IN_PLCMT_STREAM])
    private static final CurrencyConversion currencyConversion = new CurrencyConversion(networkServiceContainer)
    private static final Map AMX_CONFIG = ["adapters.amx.enabled" : "true",
                                           "adapters.amx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static PrebidServerService pbsService

    def setupSpec() {
        currencyConversion.setCurrencyConversionRatesResponse()
        pbsService = pbsServiceFactory.getService(PbsConfig.currencyConverterConfig + AMX_CONFIG)
    }

    @Override
    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PbsConfig.currencyConverterConfig + AMX_CONFIG)
    }

    def "PBS should adjust bid price for matching bidder when request has per-bidder bid adjustment factors"() {
        given: "Default bid request with bid adjustment"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactor])
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price *
                bidAdjustmentFactor

        and: "Bidder request shouldn't contain bid adjustment factors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.ext.prebid.bidAdjustmentFactors

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS should prefer bid price adjustment based on media type when request has per-media-type bid adjustment factors"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustment = randomDecimal
        def mediaTypeBidAdjustment = bidAdjustmentFactor
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                adjustments = [(GENERIC): bidAdjustment]
                mediaTypes = [(BANNER): [(GENERIC): mediaTypeBidAdjustment]]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price *
                mediaTypeBidAdjustment

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS should adjust bid price for bidder only when request contains bid adjustment for corresponding bidder"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustment = randomDecimal
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                adjustments = [(adjustmentBidder): bidAdjustment]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should not be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price

        where:
        adjustmentBidder << [RUBICON, APPNEXUS]
    }

    def "PBS should adjust bid price based on media type only when request contains corresponding media type adjustment for bidder"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustment = 0.1
        def mediaTypeBidAdjustment = bidAdjustment + 1
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                mediaTypes = [(adjustmentMediaType): [(GENERIC): mediaTypeBidAdjustment]]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should not be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price

        where:
        adjustmentMediaType << [VIDEO, NATIVE]
    }

    def "PBS should only accept positive number as a bid adjustment factor"() {
        given: "Default bid request with bid adjustment"
        def bidderName = GENERIC
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                adjustments = [(bidderName): bidAdjustmentFactor as BigDecimal]
            }
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fail the request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.bidadjustmentfactors.$bidderName.value must be a positive number")

        where:
        bidAdjustmentFactor << [MIN_ADJUST_VALUE, PBSUtils.randomNegativeNumber]
    }

    def "PBS should adjust bid price for matching bidder when request has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = impPrice
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType | ruleValue                                                       | mediaType        | bidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | BidRequest.defaultBidRequest

        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | BidRequest.defaultBidRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | BidRequest.defaultBidRequest

        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | ANY              | BidRequest.defaultBidRequest
    }

    def "PBS should adjust bid price for matching bidder and left original bidderRequest with null floors when request has bidAdjustments config"() {
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [null]

        where:
        adjustmentType | ruleValue                                                       | mediaType        | bidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | BidRequest.defaultBidRequest

        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | BidRequest.defaultBidRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | BidRequest.defaultBidRequest

        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | ANY              | BidRequest.defaultBidRequest
    }

    def "PBS should adjust bid price for matching bidder with specific dealId when request has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def dealId = PBSUtils.randomString
        def currency = USD
        def firstImpPrice = PBSUtils.randomPrice
        def secondImpPrice = PBSUtils.randomPrice
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
        }
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency, currency]
        assert bidderRequest.imp.bidFloor.sort() == [firstImpPrice, secondImpPrice].sort()

        where:
        adjustmentType | ruleValue                                                       | mediaType        | bidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | BidRequest.defaultBidRequest

        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | BidRequest.defaultBidRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | BidRequest.defaultBidRequest

        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | ANY              | BidRequest.defaultBidRequest
    }

    def "PBS should adjust bid price for matching bidder when account config has bidAdjustments"() {
        given: "BidRequest with floors"
        def impPrice = PBSUtils.randomPrice
        def currency = USD
        bidRequest.imp.first.bidFloor = impPrice
        bidRequest.imp.first.bidFloorCur = currency

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
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

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType | ruleValue                                                       | mediaType        | bidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | BidRequest.defaultBidRequest

        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | BidRequest.defaultBidRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | BidRequest.defaultBidRequest

        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | ANY              | BidRequest.defaultBidRequest
    }

    def "PBS should prioritize BidAdjustmentRule from request when account and request config bidAdjustments conflict"() {
        given: "BidRequest with floors"
        def impPrice = PBSUtils.randomPrice
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
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
        }
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType | ruleValue                                                       | mediaType        | bidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | getRandomDecimal(MIN_ADJUST_VALUE, MAX_MULTIPLIER_ADJUST_VALUE) | ANY              | BidRequest.defaultBidRequest

        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | BANNER           | BidRequest.defaultBidRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | getRandomDecimal(MIN_ADJUST_VALUE, MAX_CPM_ADJUST_VALUE)        | ANY              | BidRequest.defaultBidRequest

        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | getRandomDecimal(MIN_ADJUST_VALUE, MAX_STATIC_ADJUST_VALUE)     | ANY              | BidRequest.defaultBidRequest
    }

    def "PBS should prioritize exact bid price adjustment for matching bidder when request has exact and general bidAdjustment"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def exactRulePrice = PBSUtils.randomPrice
        def impPrice = PBSUtils.randomPrice
        def currency = USD
        def exactRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)]])
        def generalRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomPrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]
    }

    def "PBS should adjust bid price for matching bidder in provided order when bidAdjustments have multiple matching rules"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def firstRule = new AdjustmentRule(adjustmentType: firstRuleType, value: PBSUtils.randomPrice, currency: currency)
        def secondRule = new AdjustmentRule(adjustmentType: secondRuleType, value: PBSUtils.randomPrice, currency: currency)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [firstRule, secondRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        firstRuleType | secondRuleType
        MULTIPLIER    | CPM
        MULTIPLIER    | STATIC
        MULTIPLIER    | MULTIPLIER
        CPM           | CPM
        CPM           | STATIC
        CPM           | MULTIPLIER
        STATIC        | CPM
        STATIC        | STATIC
        STATIC        | MULTIPLIER
    }

    def "PBS should convert CPM currency before adjustment when it different from original response currency"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentRule = new AdjustmentRule(adjustmentType: CPM, value: PBSUtils.randomPrice, currency: GBP)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def currency = EUR
        def impPrice = PBSUtils.randomPrice
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [EUR]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
        }

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        def convertedAdjustment = CurrencyUtil.convertCurrency(adjustmentRule.value, adjustmentRule.currency, bidResponse.cur)
        def adjustedBidPrice = getAdjustedPrice(originalPrice, convertedAdjustment, adjustmentRule.adjustmentType)
        assert response.seatbid.first.bid.first.price == CurrencyUtil.convertCurrency(adjustedBidPrice, bidResponse.cur, currency)

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]
    }

    def "PBS should change original currency when static bidAdjustments and original response have different currencies"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomPrice, currency: GBP)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def currency = EUR
        def impPrice = PBSUtils.randomPrice
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
        }

        and: "Default bid response with USD currency"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted and converted to original request cur"
        assert response.seatbid.first.bid.first.price == CurrencyUtil.convertCurrency(adjustmentRule.value, adjustmentRule.currency, currency)
        assert response.cur == bidRequest.cur.first

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]
    }

    def "PBS should apply bidAdjustments after bidAdjustmentFactors when both are present"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def bidAdjustmentFactorsPrice = PBSUtils.randomPrice
        def adjustmentRule = new AdjustmentRule(adjustmentType: adjustmentType, value: PBSUtils.randomPrice, currency: currency)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactorsPrice])
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

        then: "Final bid price should be adjusted"
        def bidAdjustedPrice = originalPrice * bidAdjustmentFactorsPrice
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(bidAdjustedPrice, adjustmentRule.value, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when request has invalid value bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: currency)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.cur = [currency]
        bidRequest.imp.first.bidFloor = impPrice
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
        assert pbsService.isContainLogsByValue(errorMessage)

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType | ruleValue                       | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_MULTIPLIER_ADJUST_VALUE + 1 | ANY              | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | BANNER           | BidRequest.defaultBidRequest
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | MAX_CPM_ADJUST_VALUE + 1        | ANY              | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlacement(IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmt(IN_PLCMT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, IN_PLACEMENT_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_IN_STREAM  | getDefaultVideoRequestWithPlcmtAndPlacement(IN_PLCMT_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM, RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmtAndPlacement(null, null)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlacement(RANDOM_VIDEO_PLACEMENT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | VIDEO_OUT_STREAM | getDefaultVideoRequestWithPlcmt(RANDOM_VIDEO_PLCMT_EXCEPT_IN_STREAM)
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | MAX_STATIC_ADJUST_VALUE + 1     | ANY              | BidRequest.defaultNativeRequest
    }

    def "PBS shouldn't adjust bid price for matching bidder when request has different bidder name in bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(alias: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: PBSUtils.randomPrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when cpm or static bidAdjustments doesn't have currency value"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def adjustmentPrice = PBSUtils.randomPrice.toDouble()
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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
        assert pbsService.isContainLogsByValue(errorMessage)

        and: "Bidder request should contain original imp.floors"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when bidAdjustments have unknown mediatype"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.randomPrice
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(UNKNOWN, rule)
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when bidAdjustments have unknown adjustmentType"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def impPrice = PBSUtils.randomPrice
        def adjustmentPrice = PBSUtils.randomPrice.toDouble()
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: AdjustmentType.UNKNOWN, value: adjustmentPrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.bidFloor = impPrice
            imp.first.bidFloorCur = currency
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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
        assert pbsService.isContainLogsByValue(errorMessage)

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]
    }

    def "PBS shouldn't adjust bid price for matching bidder when multiplier bidAdjustments doesn't have currency value"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def currency = USD
        def adjustmentPrice = PBSUtils.randomPrice
        def impPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: MULTIPLIER, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp.first.tap {
                bidFloor = impPrice
                bidFloorCur = currency
            }
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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
        assert bidderRequest.cur == [currency]
        assert bidderRequest.imp.bidFloorCur == [currency]
        assert bidderRequest.imp.bidFloor == [impPrice]

        where:
        adjustmentType << [CPM, STATIC]
    }

    def "PBS should adjust bid price for matching bidder and alternate bidder code when request has per-bidder bid adjustment factors"() {
        given: "Default bid request with bid adjustment and amx bidder"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            it.ext.prebid.tap {
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
                }
                bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactor])
            }
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price *
                bidAdjustmentFactor

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS should prefer bid price adjustment based on media type and alternate bidder code when request has per-media-type bid adjustment factors"() {
        given: "Default bid request with bid adjustment"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                    adjustments = [(GENERIC): randomDecimal]
                    mediaTypes = [(BANNER): [(GENERIC): bidAdjustmentFactor]]
                }
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
                }
            }
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price *
                bidAdjustmentFactor

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS should prefer bid price adjustment based on media type and alternate bidder code when request has per-media-type bid adjustment factors with soft alias"() {
        given: "Default bid request with bid adjustment"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.aliases = [(ALIAS.value): AMX]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = null
            imp[0].ext.prebid.bidder.alias = new Generic()
            ext.prebid.tap {
                bidAdjustmentFactors = new BidAdjustmentFactors().tap {
                    adjustments = [(GENERIC): randomDecimal]
                    mediaTypes = [(BANNER): [(GENERIC): bidAdjustmentFactor]]
                }
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [GENERIC])]
                }
            }
        }

        and: "Bid response with bidder code"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            it.seatbid[0].bid[0].ext = new BidExt(bidderCode: GENERIC)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first?.bid?.first?.price == bidResponse.seatbid.first.bid.first.price *
                bidAdjustmentFactor

        and: "Response should contain repose millis with corresponding bidder"
        assert response.ext.responsetimemillis.containsKey(GENERIC.value)

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS shouldn't adjust bid price when bid adjustment rule doesn't match with bidder code"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)
        def bidAdjustmentRule = new BidAdjustmentRule((bidAdjustmentRuleBidder): [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): bidAdjustmentRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: ACUITYADS)
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted according to exact rule"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == ACUITYADS

        where:
        bidAdjustmentRuleBidder << ["alias", "aliasUpperCase", "aliasCamelCase"]
    }

    def "PBS should adjust bid price when two bid adjustment rules are compatible"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def dealId = PBSUtils.randomString
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)
        def firstBidAdjustmentRule = new BidAdjustmentRule(amx: [(dealId): [adjustmentRule]])
        def secondBidAdjustmentRule = new BidAdjustmentRule(amx: [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): firstBidAdjustmentRule,
                                                               (ANY)   : secondBidAdjustmentRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code and dealId"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: AMX)
            seatbid.first.bid.first.dealid = dealId
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

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == AMX
    }

    def "PBS should adjust bid price when bid adjustment bidder and bidder code different"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)
        def bidAdjustmentRule = new BidAdjustmentRule((bidAdjustmentRuleBidder): [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): bidAdjustmentRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: ALIAS)
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

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == ALIAS

        where:
        bidAdjustmentRuleBidder << ["alias", "aliasUpperCase", "aliasCamelCase"]
    }

    def "PBS should adjust bid price when bid adjustment bidder and bidder code same as requested"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def exactRule = new BidAdjustmentRule(amx: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): exactRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: AMX)
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

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == AMX
    }

    def "PBS should adjust bid price when bid adjustment bidder is the same as bidder code"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def exactRule = new BidAdjustmentRule(alias: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): exactRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: ALIAS)
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

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == ALIAS
    }

    def "PBS should adjust bid price when bid adjustment wildcard bidder and bidder code specified"() {
        given: "Bid request with ext.prebid.bidAdjustments and ext.prebid.alternateBidderCode"
        def exactRulePrice = PBSUtils.randomPrice
        def currency = USD
        def exactRule = new BidAdjustmentRule(wildcardBidder: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: currency)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cur = [currency]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.amx = new Amx()
            ext.prebid.tap {
                bidAdjustments = new BidAdjustment(mediaType: [(BANNER): exactRule])
                alternateBidderCodes = new AlternateBidderCodes().tap {
                    enabled = true
                    bidders = [(AMX): new BidderConfig(enabled: true, allowedBidderCodes: [AMX])]
                }
            }
        }

        and: "Default bid response with price and bidder code"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest, AMX).tap {
            cur = currency
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.ext = new BidExt(bidderCode: ALIAS)
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

        and: "Bidder request should contain currency from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur == [currency]

        and: "Response should contain adapter code"
        assert response.seatbid.bid.ext.prebid.meta.adapterCode.flatten() == [AMX]

        and: "Response should contain seatbid.seat"
        assert response.seatbid[0].seat == ALIAS
    }

    private static BigDecimal getAdjustedPrice(BigDecimal originalPrice,
                                               BigDecimal adjustedValue,
                                               AdjustmentType adjustmentType) {
        switch (adjustmentType) {
            case MULTIPLIER:
                return PBSUtils.roundDecimal(originalPrice * adjustedValue, BID_ADJUST_PRECISION)
            case CPM:
                return PBSUtils.roundDecimal(originalPrice - adjustedValue, BID_ADJUST_PRECISION)
            case STATIC:
                return adjustedValue
            default:
                return originalPrice
        }
    }

    private static BidRequest getDefaultVideoRequestWithPlacement(VideoPlacementSubtypes videoPlacementSubtypes) {
        getDefaultVideoRequestWithPlcmtAndPlacement(null, videoPlacementSubtypes)
    }

    private static BidRequest getDefaultVideoRequestWithPlcmt(VideoPlcmtSubtype videoPlcmtSubtype) {
        getDefaultVideoRequestWithPlcmtAndPlacement(videoPlcmtSubtype, null)
    }

    private static BidRequest getDefaultVideoRequestWithPlcmtAndPlacement(VideoPlcmtSubtype videoPlcmtSubtype,
                                                                          VideoPlacementSubtypes videoPlacementSubtypes) {
        BidRequest.defaultVideoRequest.tap {
            imp.first.video.tap {
                plcmt = videoPlcmtSubtype
                placement = videoPlacementSubtypes
            }
        }
    }
}
