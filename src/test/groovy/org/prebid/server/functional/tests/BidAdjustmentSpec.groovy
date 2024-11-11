package org.prebid.server.functional.tests

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.mock.services.currencyconversion.CurrencyConversionRatesResponse
import org.prebid.server.functional.model.request.auction.AdjustmentRule
import org.prebid.server.functional.model.request.auction.AdjustmentType
import org.prebid.server.functional.model.request.auction.BidAdjustment
import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidAdjustmentRule
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.CurrencyConversion
import org.prebid.server.functional.util.PBSUtils

import java.math.RoundingMode

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.Currency.CAD
import static org.prebid.server.functional.model.Currency.CHF
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD
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
import static org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes.IN_ARTICLE
import static org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes.IN_STREAM
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class BidAdjustmentSpec extends BaseSpec {

    private static final String WILDCARD = '*'
    private static final BigDecimal MIN_ADJUST_VALUE = 0
    private static final BigDecimal MAX_ADJUST_MULTIPLIER_VALUE = 99
    private static final Currency DEFAULT_CURRENCY = USD
    private static final int BID_ADJUST_PRECISION = 4
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

        where:
        bidAdjustmentFactor << [0.9, 1.1]
    }

    def "PBS should prefer bid price adjustment based on media type when request has per-media-type bid adjustment factors"() {
        given: "Default bid request with bid adjustment"
        def bidAdjustment = PBSUtils.randomDecimal
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
        def bidAdjustment = PBSUtils.randomDecimal
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
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)

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
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType | ruleValue                   | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | NATIVE           | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
    }

    def "PBS should adjust bid price for matching bidder with specific dealId when request has bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def dealId = PBSUtils.randomString
        def rule = new BidAdjustmentRule(generic: [(dealId): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)
        bidRequest.imp.add(Imp.defaultImpression)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
            seatbid.first.bid.first.dealid = dealId
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted for big with dealId"
        response.seatbid.first.bid.find { it.dealid == dealId }
        assert response.seatbid.first.bid.findAll() { it.dealid == dealId }.price == [getAdjustedPrice(originalPrice, ruleValue as BigDecimal, adjustmentType)]

        and: "Price shouldn't be updated for bid with different dealId"
        assert response.seatbid.first.bid.findAll() { it.dealid != dealId }.price == bidResponse.seatbid.first.bid.findAll() { it.dealid != dealId }.price

        and: "Response currency should stay the same"
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        assert response.seatbid.first.bid.ext.origbidcpm.sort() == bidResponse.seatbid.first.bid.price.sort()
        assert response.seatbid.first.bid.ext.first.origbidcur == bidResponse.cur
        assert response.seatbid.first.bid.ext.last.origbidcur == bidResponse.cur

        where:
        adjustmentType | ruleValue                   | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | NATIVE           | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
    }

    def "PBS should adjust bid price for matching bidder when account config has bidAdjustments"() {
        given: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB with bidAdjustments"
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
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

        where:
        adjustmentType | ruleValue                   | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | ANY              | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | ANY              | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | ANY              | BidRequest.defaultNativeRequest
    }

    def "PBS should prioritize BidAdjustmentRule from request when account and request config bidAdjustments conflict"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)

        and: "Account in the DB with bidAdjustments"
        def accountRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
        def accountConfig = new AccountAuctionConfig(bidAdjustments: BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, accountRule))
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: accountConfig))
        accountDao.save(account)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
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

        where:
        adjustmentType | ruleValue                   | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE | ANY              | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        CPM            | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | PBSUtils.randomNumber       | ANY              | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | MIN_ADJUST_VALUE            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | MIN_ADJUST_VALUE            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | MIN_ADJUST_VALUE            | ANY              | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomPrice        | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomPrice        | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomPrice        | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomPrice        | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomPrice        | ANY              | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | BANNER           | BidRequest.defaultBidRequest
        STATIC         | PBSUtils.randomNumber       | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | PBSUtils.randomNumber       | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | PBSUtils.randomNumber       | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | PBSUtils.randomNumber       | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | PBSUtils.randomNumber       | ANY              | BidRequest.defaultNativeRequest
    }

    def "PBS should prioritize exact bid price adjustment for matching bidder when request has exact and general bidAdjustment"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def exactRulePrice = PBSUtils.randomPrice
        def exactRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: exactRulePrice, currency: USD)]])
        def generalRule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomPrice, currency: USD)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = new BidAdjustment(mediaType: [(BANNER): exactRule, (ANY): generalRule])
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

        then: "Final bid price should be adjusted according to exact rule"
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, exactRulePrice, STATIC)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }
    }

    def "PBS should adjust bid price for matching bidder in provided order when bidAdjustments have multiple matching rules"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def firstRule = new AdjustmentRule(adjustmentType: MULTIPLIER, value: PBSUtils.randomPrice)
        def secondRule = new AdjustmentRule(adjustmentType: CPM, value: PBSUtils.randomPrice, currency: USD)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [firstRule, secondRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        def rawAdjustedBidPrice = getAdjustedPrice(originalPrice, firstRule.value as BigDecimal, firstRule.adjustmentType)
        def adjustedBidPrice = getAdjustedPrice(rawAdjustedBidPrice, secondRule.value as BigDecimal, secondRule.adjustmentType)
        assert response.seatbid.first.bid.first.price == adjustedBidPrice
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

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
        def adjustmentRule = new AdjustmentRule(adjustmentType: CPM, value: PBSUtils.randomPrice, currency: EUR)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        def convertedAdjustment = convertCurrency(adjustmentRule.value, EUR, USD)
        def adjustedBidPrice = getAdjustedPrice(originalPrice, convertedAdjustment, adjustmentRule.adjustmentType)
        assert response.seatbid.first.bid.first.price == adjustedBidPrice
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }
    }

    def "PBS should change original currency when static bidAdjustments and original response have different currencies"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentRule = new AdjustmentRule(adjustmentType: STATIC, value: PBSUtils.randomPrice, currency: EUR)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        assert response.seatbid.first.bid.first.price == adjustmentRule.value
        assert response.cur == adjustmentRule.currency

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }
    }

    def "PBS should apply bidAdjustments after bidAdjustmentFactors when both are present"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def bidAdjustmentFactorsPrice = PBSUtils.randomPrice
        def adjustmentRule = new AdjustmentRule(adjustmentType: adjustmentType, value: PBSUtils.randomPrice, currency: USD)
        def bidAdjustmentMultyRule = new BidAdjustmentRule(generic: [(WILDCARD): [adjustmentRule]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, bidAdjustmentMultyRule)
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactorsPrice])
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
        def bidAdjustedPrice = originalPrice * bidAdjustmentFactorsPrice
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(bidAdjustedPrice, adjustmentRule.value, adjustmentType)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == originalPrice
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when request has invalid value bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: ruleValue, currency: USD)]])
        bidRequest.ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(mediaType, rule)

        and: "Default bid response"
        def originalPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            cur = USD
            seatbid.first.bid.first.price = originalPrice
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should ignore bidadjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message ==
                ["bid adjustment from request was invalid: the found rule [adjtype=${adjustmentType}, value=${ruleValue}, currency=USD] in ${mediaType.value}.generic.* is invalid".toString()]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType | ruleValue                       | mediaType        | bidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | BANNER           | BidRequest.defaultBidRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | AUDIO            | BidRequest.defaultAudioRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | NATIVE           | BidRequest.defaultNativeRequest
        MULTIPLIER     | MAX_ADJUST_MULTIPLIER_VALUE + 1 | ANY              | BidRequest.defaultNativeRequest

        CPM            | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        CPM            | Integer.MAX_VALUE + 1           | BANNER           | BidRequest.defaultBidRequest
        CPM            | Integer.MAX_VALUE + 1           | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        CPM            | Integer.MAX_VALUE + 1           | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        CPM            | Integer.MAX_VALUE + 1           | AUDIO            | BidRequest.defaultAudioRequest
        CPM            | Integer.MAX_VALUE + 1           | NATIVE           | BidRequest.defaultNativeRequest
        CPM            | Integer.MAX_VALUE + 1           | ANY              | BidRequest.defaultNativeRequest

        STATIC         | MIN_ADJUST_VALUE - 1            | BANNER           | BidRequest.defaultBidRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | MIN_ADJUST_VALUE - 1            | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | MIN_ADJUST_VALUE - 1            | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | MIN_ADJUST_VALUE - 1            | ANY              | BidRequest.defaultNativeRequest
        STATIC         | Integer.MAX_VALUE + 1           | BANNER           | BidRequest.defaultBidRequest
        STATIC         | Integer.MAX_VALUE + 1           | VIDEO_IN_STREAM  | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_STREAM }
        STATIC         | Integer.MAX_VALUE + 1           | VIDEO_OUT_STREAM | BidRequest.defaultVideoRequest.tap { imp.first.video.placement = IN_ARTICLE }
        STATIC         | Integer.MAX_VALUE + 1           | AUDIO            | BidRequest.defaultAudioRequest
        STATIC         | Integer.MAX_VALUE + 1           | NATIVE           | BidRequest.defaultNativeRequest
        STATIC         | Integer.MAX_VALUE + 1           | ANY              | BidRequest.defaultNativeRequest
    }

    def "PBS shouldn't adjust bid price for matching bidder when request has different bidder name in bidAdjustments config"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def rule = new BidAdjustmentRule(alias: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: PBSUtils.randomPrice, currency: USD)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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

        then: "PBS should ignore bidadjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when cpm or static bidAdjustments doesn't have currency value"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.roundDecimal(PBSUtils.randomPrice, BID_ADJUST_PRECISION)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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

        then: "PBS should ignore bidadjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message ==
                ["bid adjustment from request was invalid: the found rule [adjtype=${adjustmentType}, value=${adjustmentPrice}, currency=null] in banner.generic.* is invalid".toString()]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType << [CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when bidAdjustments have unknown mediatype"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.roundDecimal(PBSUtils.randomPrice, BID_ADJUST_PRECISION)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: adjustmentType, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(UNKNOWN, rule)
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

        then: "PBS should ignore bidadjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType << [MULTIPLIER, CPM, STATIC]
    }

    def "PBS shouldn't adjust bid price for matching bidder when bidAdjustments have unknown adjustmentType"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.roundDecimal(PBSUtils.randomPrice, BID_ADJUST_PRECISION)
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: AdjustmentType.UNKNOWN, value: adjustmentPrice, currency: USD)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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

        then: "PBS should ignore bidadjustments for this request"
        assert response.seatbid.first.bid.first.price == originalPrice
        assert response.cur == bidResponse.cur

        and: "Should add a warning when in debug mode"
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message ==
                ["bid adjustment from request was invalid: the found rule [adjtype=UNKNOWN, value=${adjustmentPrice}, currency=USD] in banner.generic.* is invalid".toString()]

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }
    }

    def "PBS shouldn't adjust bid price for matching bidder when multiplier bidAdjustments doesn't have currency value"() {
        given: "Default BidRequest with ext.prebid.bidAdjustments"
        def adjustmentPrice = PBSUtils.randomPrice
        def rule = new BidAdjustmentRule(generic: [(WILDCARD): [new AdjustmentRule(adjustmentType: MULTIPLIER, value: adjustmentPrice, currency: null)]])
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustments = BidAdjustment.getDefaultWithSingleMediaTypeRule(BANNER, rule)
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
        assert response.seatbid.first.bid.first.price == getAdjustedPrice(originalPrice, adjustmentPrice, MULTIPLIER)
        assert response.cur == bidResponse.cur

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        and: "Response shouldn't contain any warnings"
        assert !response.ext.warnings

        and: "Original bid price and currency should be presented in bid.ext"
        verifyAll(response.seatbid.first.bid.first.ext) {
            origbidcpm == bidResponse.seatbid.first.bid.first.price
            origbidcur == bidResponse.cur
        }

        where:
        adjustmentType << [CPM, STATIC]
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
}
