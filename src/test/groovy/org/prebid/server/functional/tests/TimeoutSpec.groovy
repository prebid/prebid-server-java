package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.utility.MountableFile
import spock.lang.IgnoreRest
import spock.lang.Shared

import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.APP_WORKDIR

class TimeoutSpec extends BaseSpec {

    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final int MIN_TIMEOUT = PBSUtils.getRandomNumber(50, 150)
    private static final Long MAX_AUCTION_BIDDER_TIMEOUT = 3000
    private static final Long MIN_AUCTION_BIDDER_TIMEOUT = 1000
    private static final Map PBS_CONFIG = ["auction.biddertmax.max": MAX_AUCTION_BIDDER_TIMEOUT as String,
                                           "auction.biddertmax.min": MIN_AUCTION_BIDDER_TIMEOUT as String]

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

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
        def storedRequest = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request with warmup"
        prebidServerService.withWarmup().sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, timeout)
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
        def storedRequest = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, timeout)

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
        def storedRequestModel = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, MAX_TIMEOUT)

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
        def storedRequestModel = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, MAX_TIMEOUT)
    }

    def "PBS should take data by priority when request, stored request, default request are defined"() {
        given: "Default request with timeout"
        def defaultRequestModel = new BidRequest(tmax: defaultRequestTmax)
        def defaultRequest = PBSUtils.createJsonFile(defaultRequestModel)

        and: "Pbs config with default request"
        def pbsContainer = new PrebidServerContainer(
                ["default-request.file.path": APP_WORKDIR + defaultRequest.fileName,
                 "auction.biddertmax.max"   : MAX_TIMEOUT as String]).tap {
            withCopyFileToContainer(MountableFile.forHostPath(defaultRequest), APP_WORKDIR)
        }
        pbsContainer.start()
        def pbsService = new PrebidServerService(pbsContainer)

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
        def storedRequest = StoredRequest.getStoredRequest(bidRequest.ext.prebid.storedRequest.id, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request with warmup"
        def response = pbsService.withWarmup().sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        then: "Bidder request should contain correct tmax"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, DEFAULT_TIMEOUT)

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
        assert isInternalProcessingTime(bidderRequest.tmax, timeout)
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
        assert isInternalProcessingTime(bidderRequest.tmax, timeout)

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
        assert isInternalProcessingTime(bidderRequest.tmax, MAX_TIMEOUT)

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
        assert isInternalProcessingTime(bidderRequest.tmax, MAX_TIMEOUT)
    }

    def "PBS should choose min timeout form config for bidder request when in request value lowest that in auction.biddertmax.min"() {
        given: "PBS config with percent"
        def minBidderTmax = PBSUtils.getRandomNumber(MIN_TIMEOUT, MAX_TIMEOUT)
        def prebidServerService = pbsServiceFactory.getService(["auction.biddertmax.min": minBidderTmax as String,
                                                                "auction.biddertmax.max": MAX_TIMEOUT as String])

        and: "Default basic BidRequest"
        def timeout = PBSUtils.getRandomNumber(0, minBidderTmax)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
        }

        when: "PBS processes auction request with warmup"
        def bidResponse = prebidServerService.withWarmup().sendAuctionRequest(bidRequest)

        then: "Bidder request should contain min value from auction.biddertmax.min config"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == minBidderTmax as Long

        and: "PBS response should contain tmax from request"
        assert bidResponse?.ext?.tmaxrequest == timeout as Long
    }

    @IgnoreRest
    def "PBS should change timeout for bidder due to percent in auction.biddertmax.percent"() {
        given: "PBS config with percent"
        def percent = PBSUtils.getRandomNumber(2, 98)
        def pbsConfig = ["auction.biddertmax.percent": percent as String,
                         "auction.biddertmax.max"    : MAX_TIMEOUT as String,
                         "auction.biddertmax.min"    : MIN_TIMEOUT as String]
        def prebidServerService = pbsServiceFactory.getService(
                pbsConfig)

        and: "Default basic BidRequest with generic bidder"
        def timeout = randomTimeout
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
        }

        when: "PBS processes auction request with warmup"
        def bidResponse = prebidServerService.withWarmup().sendAuctionRequest(bidRequest)

        then: "Bidder request should contain percent of request value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, getPercentOfValue(percent, timeout))

        and: "PBS response should contain tmax from request"
        assert bidResponse?.ext?.tmaxrequest == timeout as Long

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should apply action bidder min timeout when adapters.generic.tmax-deduction-ms is big value"() {
        given: "PBS config with adapters.generic.tmax-deduction-ms"
        def pbsConfig = PBS_CONFIG + ["adapters.generic.tmax-deduction-ms": PBSUtils.getRandomNumber(MAX_AUCTION_BIDDER_TIMEOUT as int) as String]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = randomTimeout
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain min"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == MIN_AUCTION_BIDDER_TIMEOUT

        and: "PBS response should contain tmax"
        assert bidResponse?.ext?.tmaxrequest == MAX_AUCTION_BIDDER_TIMEOUT

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    @IgnoreRest
    def "PBS should resolve timeout as usual when adapters.generic.tmax-deduction-ms specifies zero"() {
        given: "PBS config with adapters.generic.tmax-deduction-ms"
        def pbsConfig = ["adapters.generic.tmax-deduction-ms": "0"] + PBS_CONFIG
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic BidRequest with generic bidder"
        def timeout = PBSUtils.getRandomNumber(
                MIN_AUCTION_BIDDER_TIMEOUT as int,
                MAX_AUCTION_BIDDER_TIMEOUT as int)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
        }

        when: "PBS processes auction request with warmup"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain right value in tmax"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, timeout)

        and: "PBS response should contain tmax"
        assert bidResponse?.ext?.tmaxrequest == timeout as Long

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should properly resolve tmax deduction ms when adapters.generic.tmax-deduction-ms specified"() {
        given: "PBS config with adapters.generic.tmax-deduction-ms"
        def genericDeductionMs = PBSUtils.getRandomNumber(100, 300)
        def randomTimeout = PBSUtils.getRandomNumber(MIN_AUCTION_BIDDER_TIMEOUT + genericDeductionMs as int, MAX_AUCTION_BIDDER_TIMEOUT as int)
        def pbsConfig = PBS_CONFIG + ["adapters.generic.tmax-deduction-ms": genericDeductionMs as String]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = randomTimeout
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain right value in tmax"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert isInternalProcessingTime(bidderRequest.tmax, randomTimeout)

        and: "PBS response should contain tmax"
        assert bidResponse?.ext?.tmaxrequest == randomTimeout as Long

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    private static long getPercentOfValue(int percent, int value) {
        (percent * value) / 100.0 as Long
    }

    private static boolean isInternalProcessingTime(long bidderRequestTimeout, long requestTimeout) {
        0 < requestTimeout - bidderRequestTimeout
    }
}
