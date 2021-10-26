package org.prebid.server.functional

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

class AmpSpec extends BaseSpec {

    private static final int DEFAULT_TIMEOUT = getTimeout()

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                                                                            "auction.default-timeout-ms": DEFAULT_TIMEOUT as String])

    def "PBS should apply timeout from stored request when it's not specified in the request"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = null
        }

        and: "Default stored request with timeout"
        def timeout = PBSUtils.getRandomNumber(0, MAX_TIMEOUT)
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == timeout as Long
    }

    @Unroll
    def "PBS should prefer timeout from the request when stored request timeout is #tmax"() {
        given: "Default AMP request with timeout"
        def timeout = PBSUtils.getRandomNumber(0, MAX_TIMEOUT)
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = timeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = tmax
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == timeout as Long

        where:
        tmax << [null, PBSUtils.getRandomNumber(0, MAX_TIMEOUT)]
    }

    @Unroll
    def "PBS should honor max timeout from the settings"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = ampRequestTimeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == MAX_TIMEOUT as Long

        where:
        ampRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1   || null
        null              || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1   || MAX_TIMEOUT + 1
    }

    def "PBS should honor default timeout"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = null
        }

        and: "Default stored request without timeout"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == DEFAULT_TIMEOUT as Long
    }

    private static int getTimeout() {
        PBSUtils.getRandomNumber(MIN_TIMEOUT, MAX_TIMEOUT)
    }
}
