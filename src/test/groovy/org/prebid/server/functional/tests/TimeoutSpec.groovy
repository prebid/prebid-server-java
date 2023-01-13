package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.utility.MountableFile
import spock.lang.Shared

import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.APP_WORKDIR

class TimeoutSpec extends BaseSpec {

    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final int MIN_TIMEOUT = 100

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.biddertmax.max"    : MAX_TIMEOUT as String,
                                                                            "auction.biddertmax.min"    : MIN_TIMEOUT as String])

    @Override
    def setupSpec(){
        prebidServerService.sendAuctionRequest(BidRequest.defaultBidRequest)
        bidder.setResponse()
    }

    def "PBS should apply timeout from stored request when it's not specified in the auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def timeout = getRandomTimeout()
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert closeValue(bidderRequest.tmax, timeout)
    }

    def "PBS should prefer timeout from the auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def timeout = getRandomTimeout()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = tmaxStoredRequest
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert closeValue(bidderRequest.tmax, timeout)

        where:
        tmaxStoredRequest << [null, getRandomTimeout()]
    }

    def "PBS should honor max timeout from the settings for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = auctionRequestTimeout
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert closeValue(bidderRequest.tmax, MAX_TIMEOUT)

        where:
        auctionRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1       || null
        null                  || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1       || MAX_TIMEOUT + 1
    }

    def "PBS should honor default timeout for auction request"() {
        given: "Default basic BidRequest without timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request without timeout"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert closeValue(bidderRequest.tmax, MAX_TIMEOUT)
    }

    def "PBS should take data by priority when request, stored request, default request are defined"() {
        given: "Default request with timeout"
        def defaultRequestModel = new BidRequest(tmax: defaultRequestTmax)
        def defaultRequest = PBSUtils.createJsonFile(defaultRequestModel)

        and: "Pbs config with default request"
        def pbsContainer = new PrebidServerContainer(
                ["default-request.file.path" : APP_WORKDIR + defaultRequest.fileName,
                 "auction.biddertmax.max"    : MAX_TIMEOUT as String]).tap {
            withCopyFileToContainer(MountableFile.forHostPath(defaultRequest), APP_WORKDIR) }
        pbsContainer.start()
        def pbsService = new PrebidServerService(pbsContainer)

        and: "Idle request PBS for stabilize timeout"
        pbsService.sendAuctionRequest(BidRequest.defaultBidRequest)

        and: "Default basic BidRequest with timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = requestTmax
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = storedRequestTmax as Long
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        then: "Bidder request should contain correct tmax"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert closeValue(bidderRequest.tmax, DEFAULT_TIMEOUT)

        cleanup: "Stop container with default request"
        pbsContainer.stop()

        where:
        requestTmax     | storedRequestTmax  | defaultRequestTmax
        DEFAULT_TIMEOUT | getRandomTimeout() | getRandomTimeout()
        null            | DEFAULT_TIMEOUT    | getRandomTimeout()
        null            | null               | DEFAULT_TIMEOUT
    }

    def "PBS should apply timeout from stored request when it's not specified in the request"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = null
        }

        and: "Default stored request with timeout"
        def timeout = getRandomTimeout()
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert closeValue(bidderRequest.tmax, timeout)
    }

    def "PBS should prefer timeout from the request when stored request timeout is #tmax"() {
        given: "Default AMP request with timeout"
        def timeout = getRandomTimeout()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = timeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = tmax
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert closeValue(bidderRequest.tmax, timeout)

        where:
        tmax << [null, getRandomTimeout()]
    }

    def "PBS should honor max timeout from the settings"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = ampRequestTimeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert closeValue(bidderRequest.tmax, MAX_TIMEOUT)

        where:
        ampRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1   || null
        null              || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1   || MAX_TIMEOUT + 1
    }

    def "PBS should honor max timeout when timeout and tmax absent in request"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = null
        }

        and: "Default stored request without timeout"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert closeValue(bidderRequest.tmax, MAX_TIMEOUT)
    }

    def "PBS amp should return error when timeout from biddertmax.min is lowest"() {
        given: "PBS config with biddertmax.min = 5"
        def bidderTMaxMin = "1"
        def prebidServerService = pbsServiceFactory.getService(["auction.biddertmax.max"    : MAX_TIMEOUT as String,
                                                                                "auction.biddertmax.min"    : bidderTMaxMin])

        and: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = null
        }

        and: "Default stored request with min tmax"
        def minTmax = 5
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = minTmax
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes amp request"
        def bidResponse = prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the min from the stored request"
        assert bidResponse?.ext?.debug?.resolvedRequest?.tmax == minTmax

        and: "PBS should send to bidder tmax form auction.biddertmax.min config"
        assert bidResponse.ext.debug.httpcalls[BidderName.GENERIC.value]*.requestBody[0].contains("\"tmax\":${bidderTMaxMin}")

        and: "Bid response should shutdown by timeout from stored request"
        def errors = bidResponse.ext?.errors
        assert errors[ErrorType.GENERIC]*.code == [1]
        assert errors[ErrorType.GENERIC]*.message == ["Timeout has been exceeded"]
    }

    def "PBS auction should return error when timeout from biddertmax.min is lowest"() {
        given: "PBS config with biddertmax.min = 5"
        def bidderTMaxMin = "1"
        def prebidServerService = pbsServiceFactory.getService(["auction.biddertmax.max"    : MAX_TIMEOUT as String,
                                                                                "auction.biddertmax.min"    : bidderTMaxMin])

        and: "Default BidRequest without timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with min tmax"
        def minTmax = 5
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            tmax = minTmax
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the min from the stored request"
        assert bidResponse?.ext?.debug?.resolvedRequest?.tmax == minTmax

        and: "PBS should send to bidder tmax form auction.biddertmax.min config"
        assert bidResponse.ext.debug.httpcalls[BidderName.GENERIC.value]*.requestBody[0].contains("\"tmax\":${bidderTMaxMin}")

        and: "Bid response should shutdown by timeout from stored request"
        def errors = bidResponse.ext?.errors
        assert errors[ErrorType.GENERIC]*.code == [1]
        assert errors[ErrorType.GENERIC]*.message == ["Timeout has been exceeded"]
    }

    private static boolean closeValue(Long bidderRequestTmax,  Long requestTimeout){
        def internalProcess = requestTimeout - bidderRequestTmax
        0 < internalProcess && 85 >= internalProcess
    }
}
