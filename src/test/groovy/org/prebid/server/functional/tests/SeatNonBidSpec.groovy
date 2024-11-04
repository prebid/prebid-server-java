package org.prebid.server.functional.tests

import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountBidValidationConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.Adm
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils

import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400
import static org.mockserver.model.HttpStatusCode.INTERNAL_SERVER_ERROR_500
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.HttpStatusCode.PROCESSING_102
import static org.mockserver.model.HttpStatusCode.SERVICE_UNAVAILABLE_503
import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.config.BidValidationEnforcement.ENFORCE
import static org.prebid.server.functional.model.request.auction.DebugCondition.DISABLED
import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.SecurityLevel.SECURE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_BIDDER_UNREACHABLE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_INVALID_BID_RESPONSE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_NO_BID
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE_SIZE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_TIMED_OUT
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class SeatNonBidSpec extends BaseSpec {

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder didn't bid"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = requestWithAllBidStatus

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, responseStatusCode)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_NO_BID

        where:
        responseStatusCode << [OK_200, NO_CONTENT_204]
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with invalid bid response status code"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = requestWithAllBidStatus

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        def statusCode = PBSUtils.getRandomElement([PROCESSING_102, BAD_REQUEST_400, INTERNAL_SERVER_ERROR_500])
        bidder.setResponse(bidRequest.id, bidResponse, statusCode)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_INVALID_BID_RESPONSE
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with bidder unreachable status code"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = requestWithAllBidStatus

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, SERVICE_UNAVAILABLE_503)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_BIDDER_UNREACHABLE
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with invalid creative size status code"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = requestWithAllBidStatus

        and: "Default bidder response with creative size adjustment"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first.tap {
                bid.first.height = bidRequest.imp.first.banner.format.first.height + 1
                bid.first.weight = bidRequest.imp.first.banner.format.first.weight + 1
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(bidValidations:
                new AccountBidValidationConfig(bannerMaxSizeEnforcement: ENFORCE)))
        def account = new Account(status: ACTIVE, uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE_SIZE
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with not secure status code"() {
        given: "PBS with secure-markup enforcement"
        def pbsService = pbsServiceFactory.getService(["auction.validations.secure-markup": ENFORCE.value])

        and: "A bid request with secure and returnAllBidStatus flags set"
        def bidRequest = requestWithAllBidStatus.tap {
            imp[0].secure = SECURE
        }

        and: "A default bidder response without a valid bid"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first.bid.first.tap {
                it.adm = new Adm(assets: [Asset.getImgAsset("http://secure-assets.${PBSUtils.randomString}.com")])
            }
        }

        and: "Setting the bidder response"
        bidder.setResponse(bidRequest.id, storedBidResponse)

        when: "PBS processes the auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "The PBS response should contain seatNonBid for the called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=true and bidder successfully bids"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = requestWithAllBidStatus

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
        assert response.seatbid
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and debug=#debug and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug = #debug"
        def bidRequest = requestWithAllBidStatus.tap {
            ext.prebid.debug = debug
        }

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_NO_BID

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        where:
        debug << [ENABLED, DISABLED, null]
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=false and debug=#debug and requested bidder didn't bid for any reason"() {
        given: "Default bid request with disabled returnAllBidStatus and debug = #debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = false
            ext.prebid.debug = debug
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid and seatbid for called bidder"
        assert !response.ext.seatnonbid
        assert !response.seatbid

        where:
        debug << [ENABLED, DISABLED, null]
    }

    def "PBS should populate seatNonBid when bidder is rejected due to timeout"() {
        given: "PBS config with min and max time-out"
        def timeout = 50
        def pbsService = pbsServiceFactory.getService(["auction.biddertmax.min": timeout as String])

        and: "Default bid request with max timeout"
        def bidRequest = requestWithAllBidStatus.tap {
            tmax = timeout
        }

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response with delay"
        bidder.setResponse(bidRequest.id, bidResponse, timeout + 1)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid and contain errors"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT
    }

    def "PBS should populate seatNonBid when filter-imp-media-type=true and imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "banner"])

        and: "Default basic BidRequest with banner"
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contains seatNonBid"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=true and storedAuctionResponse present"() {
        given: "Default bid request with returnAllBidStatus and storedAuction"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
        assert response.seatbid
    }

    private static BidRequest getRequestWithAllBidStatus(DistributionChannel channel = SITE) {
        BidRequest.getDefaultBidRequest(channel).tap {
            ext.prebid.returnAllBidStatus = true
        }
    }
}
