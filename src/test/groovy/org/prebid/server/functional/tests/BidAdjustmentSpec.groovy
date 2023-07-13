package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.BANNER
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.NATIVE
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class BidAdjustmentSpec extends BaseSpec {

    def "PBS should adjust bid price for matching bidder when request has per-bidder bid adjustment factors"() {
        given: "Default bid request with bid adjustment"
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC): bidAdjustmentFactor])
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first()?.bid?.first()?.price == bidResponse.seatbid.first().bid.first().price *
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
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should be adjusted"
        assert response?.seatbid?.first()?.bid?.first()?.price == bidResponse.seatbid.first().bid.first().price *
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
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should not be adjusted"
        assert response?.seatbid?.first()?.bid?.first()?.price == bidResponse.seatbid.first().bid.first().price

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
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Final bid price should not be adjusted"
        assert response?.seatbid?.first()?.bid?.first()?.price == bidResponse.seatbid.first().bid.first().price

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
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should fail the request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.bidadjustmentfactors.$bidderName.value must be a positive number")

        where:
        bidAdjustmentFactor << [0, PBSUtils.randomNegativeNumber]
    }
}
