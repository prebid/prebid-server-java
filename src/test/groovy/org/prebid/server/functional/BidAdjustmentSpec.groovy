package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidAdjustmentFactors
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import static java.math.RoundingMode.HALF_UP
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.BANNER
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO
import static org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType.VIDEO_OUTSTREAM

class BidAdjustmentSpec extends BaseSpec {

    @Unroll
    def "PBS should modify resulting bid when bid adjustment factors specified"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC.value): bidAdjustment as BigDecimal])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Price should changed according to bid adjustment"
        assert response.seatbid[0]?.bid[0]?.price == bidResponse.seatbid[0].bid[0].price * bidAdjustment

        where:
        bidAdjustment << [0.1, 1, 10]
    }

    def "PBS should prefer media type bid adjustment when ext.prebid.bidadjustmentfactors.BIDDER specified"() {
        given: "BidRequest with bid adjustment"
        def bidAdjustment = randomBidAdjustment
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(
                    adjustments: [(GENERIC.value): randomBidAdjustment],
                    mediaTypes: [(BANNER): [(GENERIC): bidAdjustment]])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Price should be changed according to media type bid adjustment"
        assert response.seatbid[0]?.bid[0]?.price == bidResponse.seatbid[0].bid[0].price * bidAdjustment
    }

    @Unroll
    def "PBS should apply media type bid adjustment for appropriate media type"() {
        given: "BidRequest with banner,video imp, bid adjustment"
        def bannerBidAdjustment = randomBidAdjustment
        def videoBidAdjustment = randomBidAdjustment
        def videoImp = Imp.videoImpression.tap {
            video.placement = placementValue
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            addImp(videoImp)
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(
                    adjustments: [(GENERIC.value): randomBidAdjustment],
                    mediaTypes: [(BANNER)   : [(GENERIC): bannerBidAdjustment],
                                 (mediaType): [(GENERIC): videoBidAdjustment]])
        }

        and: "Bids for banner, video"
        def bannerPrice = PBSUtils.randomPrice
        def videoPrice = PBSUtils.randomPrice
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].price = bannerPrice
            seatbid[0].bid[1].adm = PBSUtils.randomString
            seatbid[0].bid[1].nurl = PBSUtils.randomString
            seatbid[0].bid[1].price = videoPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Price should changed according to media type bid adjustment "
        def responseBannerBid = response.seatbid[0]?.bid?.find { it.ext.prebid.type == MediaType.BANNER }
        def responseVideoBid = response.seatbid[0]?.bid?.find { it.ext.prebid.type == MediaType.VIDEO }

        assert responseBannerBid.price == bannerPrice * bannerBidAdjustment
        assert responseVideoBid.price == videoPrice * videoBidAdjustment

        where:
        placementValue | mediaType
        1              | VIDEO
        2              | VIDEO_OUTSTREAM
    }

    def "PBS should not apply bid adjustment for not matched bidder"() {
        given: "BidRequest with bid adjustment"
        def bidAdjustment = randomBidAdjustment
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(
                    adjustments: [(GENERIC.value): bidAdjustment],
                    mediaTypes: [(BANNER): [(RUBICON): randomBidAdjustment]])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Price should changed according to bid adjustment"
        assert response.seatbid[0]?.bid[0]?.price == bidResponse.seatbid[0].bid[0].price * bidAdjustment
    }

    @Unroll
    def "PBS should emit error when bid adjustment factor is not positive number"() {
        given: "BidRequest with bid adjustment"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(GENERIC.value): bidAdjustment as BigDecimal])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("request.ext.prebid.bidadjustmentfactors.$GENERIC.value must be a positive number")

        where:
        bidAdjustment << [0, PBSUtils.randomNegativeNumber]
    }

    def "PBS should emit error when bid adjustment bidder is bogus"() {
        given: "BidRequest with bid adjustment"
        def bogusBidder = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(bogusBidder): randomBidAdjustment])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody.contains("request.ext.prebid.bidadjustmentfactors.$bogusBidder is not a known bidder or alias")
    }

    @Unroll
    def "PBS bid adjustment should not influence other bidders"() {
        given: "BidRequest with bid adjustment"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.bidAdjustmentFactors = new BidAdjustmentFactors(adjustments: [(RUBICON.value): randomBidAdjustment])
        }

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Price should not be changed"
        assert response.seatbid[0]?.bid[0]?.price == bidResponse.seatbid[0].bid[0].price
    }

    private static BigDecimal getRandomBidAdjustment() {
        BigDecimal.valueOf(PBSUtils.getFractionalRandomNumber(0, 2))
                  .setScale(3, HALF_UP)
    }
}
