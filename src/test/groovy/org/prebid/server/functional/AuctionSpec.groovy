package org.prebid.server.functional

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.StoredRequestExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

class AuctionSpec extends BaseSpec {

    private static final int MAX_TIMEOUT = 5000
    private static final int DEFAULT_TIMEOUT = PBSUtils.getRandomNumber(1000, MAX_TIMEOUT)
    private static final String PBS_VERSION_HEADER = "pbs-java/$PBS_VERSION"
    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                                                                            "auction.default-timeout-ms": DEFAULT_TIMEOUT as String])

    @Unroll
    def "PBS should return version in response header for auction request for #description"() {

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == PBS_VERSION_HEADER

        where:
        bidRequest                   || description
        BidRequest.defaultBidRequest || "valid bid request"
        new BidRequest()             || "invalid bid request"
    }

    def "PBS should apply timeout from stored request when it's not specified in the auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new StoredRequestExt(id: PBSUtils.randomNumber.toString())
        }

        and: "Default stored request with timeout"
        def timeout = PBSUtils.getRandomNumber(0, MAX_TIMEOUT)
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getAuctionDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == timeout as Long
    }

    @Unroll
    def "PBS should prefer timeout from the auction request when stored request timeout is #tmax"() {
        given: "Default basic BidRequest with generic bidder"
        def timeout = PBSUtils.getRandomNumber(0, MAX_TIMEOUT)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
            ext.prebid.storedRequest = new StoredRequestExt(id: PBSUtils.randomNumber.toString())
        }

        and: "Default stored request"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            it.tmax = tmaxStoredRequest
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getAuctionDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == timeout as Long

        where:
        tmaxStoredRequest << [null, PBSUtils.getRandomNumber(0, MAX_TIMEOUT)]
    }

    @Unroll
    def "PBS should honor max timeout from the settings for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = autcionRequestTimeout
            ext.prebid.storedRequest = new StoredRequestExt(id: PBSUtils.randomNumber.toString())
        }

        and: "Default stored request"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getAuctionDbStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == MAX_TIMEOUT as Long

        where:
        autcionRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1       || null
        null                  || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1       || MAX_TIMEOUT + 1
    }

    def "PBS should honor default timeout for auction request"() {
        given: "Default basic BidRequest without timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new StoredRequestExt(id: PBSUtils.randomNumber.toString())
        }

        and: "Default stored request without timeout"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getAuctionDbStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == DEFAULT_TIMEOUT as Long
    }
}
