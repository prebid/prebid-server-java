package org.prebid.server.functional.tests

import org.apache.commons.lang3.StringUtils
import org.junit.Ignore
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.auction.BidderCallType.STORED_BID_RESPONSE
import static org.prebid.server.functional.model.response.auction.ImpRejectionReason.NO_BID

@Ignore
class DebugSpec extends BaseSpec {

    private static final String overrideToken = PBSUtils.randomString

    def "PBS should return debug information when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = debug
        bidRequest.test = test

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        where:
        debug | test
        1     | null
        1     | 0
        null  | 1
    }

    def "PBS shouldn't return debug information when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = test
        bidRequest.test = test

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain ext.debug"
        assert !response.ext?.debug

        where:
        debug | test
        0     | null
        null  | 0
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999] // [10003]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $GENERIC.value" as String]
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        //TODO possibly change message after clarifications
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for account"]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10003 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $GENERIC.value" as String]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = true is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]
    }

    def "PBS should use default values = true for bidder-level setting debug.allow and account-level setting debug-allowed when they are not specified"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = #debugAllowedConfig and account-level setting debug-allowed = #debugAllowedAccount is overridden by x-pbs-debug-override header"() {
        given: "PBS with debug configuration"
        def pbsService = pbsServiceFactory.getService(pbdConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: debugAllowedAccount))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": overrideToken])

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings

        where:
        debugAllowedConfig | debugAllowedAccount | pbdConfig
        false              | true                | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "false"]
        true               | false               | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "true"]
        false              | false               | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "false"]
    }

    def "PBS should not return debug information when x-pbs-debug-override header is incorrect"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["debug.override-token": overrideToken])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": headerValue])

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]

        where:
        headerValue << [StringUtils.swapCase(overrideToken), PBSUtils.randomString]
    }

    @PendingFeature
    def "PBS AMP should return debug information when request flag is #requestDebug and store request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug information"
        assert response.ext?.debug

        where:
        requestDebug || storedRequestDebug
        1            || 0
        1            || 1
        1            || null
        null         || 1
    }

    def "PBS AMP shouldn't return debug information when request flag is #requestDebug and stored request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response shouldn't contain debug information"
        assert !response.ext?.debug

        where:
        requestDebug || storedRequestDebug
        0            || 1
        0            || 0
        0            || null
        null         || 0
        null         || null
    }

    def "PBS shouldn't populate call type when it's default bidder call"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain call type"
        assert response.ext?.debug?.httpcalls[GENERIC.value].first().callType == null

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return STORED_BID_RESPONSE call type when call from stored bid response "() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain call type STORED_BID_RESPONSE"
        assert response.ext?.debug?.httpcalls[GENERIC.value].first().callType == STORED_BID_RESPONSE

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: true)
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID
    }

    def "PBS should populate seatNonBid and debug when returnAllBidStatus=true, debug=0 and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: true, debug: 0)
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID

        and: "PBS response shouldn't contain debug"
        assert !response?.ext?.debug
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=false and bidder didn't bid for any reason"() {
        given: "Default bid request with disabled returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: false)
        }

        and: "Default bidder response without bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
        assert !response.seatbid
    }

    def "PBS shouldn't populate seatNonBid when debug=0 and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug, test"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: false, debug: 0)
        }

        and: "Default bidder response without bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid for called bidder"
        assert !response.ext.seatnonbid
    }

    def "PBS shouldn't populate seatNonBid with successful bids"() {
        given: "Default bid request with enabled returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

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
}
