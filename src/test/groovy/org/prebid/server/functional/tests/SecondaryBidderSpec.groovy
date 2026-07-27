package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_TIMED_OUT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SecondaryBidderSpec extends BaseSpec {

    private static final String OPENX_AUCTION_ENDPOINT = "/openx-auction"
    private static final String GENERIC_ALIAS_AUCTION_ENDPOINT = "/generic-alias-auction"
    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.${OPENX.value}.enabled" : "true",
            "adapters.${OPENX.value}.endpoint": "$networkServiceContainer.rootUri$OPENX_AUCTION_ENDPOINT".toString()]
    private static final Map<String, String> GENERIC_ALIAS_CONFIG = [
            "adapters.${GENERIC.value}.aliases.${ALIAS}.enabled" : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS}.endpoint": "$networkServiceContainer.rootUri$GENERIC_ALIAS_AUCTION_ENDPOINT".toString()]
    private static final String WARNING_TIME_OUT_MESSAGE = "secondary bidder timed out, auction proceeded"
    private static final Long RESPONSE_DELAY_MILLISECONDS = 5000
    private static final Bidder openXBidder = new Bidder(networkServiceContainer, OPENX_AUCTION_ENDPOINT)
    private static final Bidder genericAliasBidder = new Bidder(networkServiceContainer, GENERIC_ALIAS_AUCTION_ENDPOINT)

    @Shared
    PrebidServerService pbsServiceWithOpenXBidder = pbsServiceFactory.getService(OPENX_CONFIG + GENERIC_ALIAS_CONFIG)

    @Override
    def cleanupSpec() {
        pbsServiceFactory.removeContainer(OPENX_CONFIG + GENERIC_ALIAS_CONFIG)
    }

    def cleanup() {
        openXBidder.reset()
        genericAliasBidder.reset()
    }

    def "PBS shouldn't emit warning when secondary bidders account config set to #secondaryBidder"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [secondaryBidder])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should process bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid

        where:
        secondaryBidder << [null, UNKNOWN]
    }

    def "PBS should treat all bidders as primary when all requested bidders in secondary bidders account config"() {
        given: "Default basic BidRequest with generic and openx bidder"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX])

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [GENERIC, OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx response"
        openXBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed generic request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1

        and: "PBs should processed openx request"
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert openXBidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS shouldn't wait on non-prioritized bidder when primary bidder from account configuration responds"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX])

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = secondaryBiddersConfig
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder call"
        assert bidder.getBidderRequests(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX.value]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid for openX bidder"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        where:
        secondaryBiddersConfig << [
                new AccountAuctionConfig(secondaryBidders: [OPENX]),
                new AccountAuctionConfig(secondaryBiddersSnakeCase: [OPENX])
        ]
    }

    def "PBS shouldn't treat alias as secondary when root bidder is secondary in account config"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([OPENX, ALIAS]).tap {
            it.ext.prebid.aliases = [(ALIAS.value): OPENX]
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        and: "Set up openx alias bidder response"
        genericAliasBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBS should process bidder request"
        assert bidder.getBidderRequest(bidRequest.id)
        assert genericAliasBidder.getBidderRequest(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response should contain openX alias and generic"
        assert bidResponse.seatbid.seat.sort() == [ALIAS, GENERIC].sort()

        and: "PBs response should contain response body from generic and alias bidder"
        def httpCalls = bidResponse?.ext?.debug?.httpcalls
        assert httpCalls[GENERIC.value]?.responseBody
        assert httpCalls[ALIAS.value]?.responseBody

        and: "PBS response shouldn't contain response body from openX bidder"
        assert !httpCalls[OPENX.value]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT
    }

    def "PBS shouldn't wait on secondary bidder when alias bidder respond with delay"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([OPENX, ALIAS]).tap {
            it.ext.prebid.aliases = [(ALIAS.value): OPENX]
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [ALIAS])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        genericAliasBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        and: "Set up openx alias bidder response"
        openXBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should process bidder request"
        assert bidder.getBidderRequest(bidRequest.id)
        assert genericAliasBidder.getBidderRequest(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs repose shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[ALIAS.value]?.responseBody

        and: "PBS should contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx alias"
        assert bidResponse.ext?.warnings[ErrorType.ALIAS].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT
    }

    def "PBS should pass auction as usual when primary bidder responds after secondary"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX])

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [GENERIC])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        def openXRandomDelay = bidRequest.tmax - PBSUtils.getRandomNumber(100, 500)
        openXBidder.setResponseWithDelay(openXRandomDelay)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should process bidder request"
        assert bidder.getBidderRequest(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response should contain generic and openX bidders"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX].sort()

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS shouldn't emit warning when secondary bidders request config set to #secondaryBidder"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enabledReturnAllBidStatus()
            ext.prebid.secondaryBidders = [secondaryBidder]
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should process bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid

        where:
        secondaryBidder << [null, UNKNOWN]
    }

    def "PBS should treat all bidders as primary when all requested bidders in secondary bidders request config"() {
        given: "Default basic BidRequest with generic and openx bidder"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX]).tap {
            ext.prebid.secondaryBidders = [GENERIC, OPENX]
        }

        and: "Set up openx response"
        openXBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed generic request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1

        and: "PBs should processed openx request"
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert openXBidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS shouldn't wait on non-prioritized bidder when primary bidder from request configuration responds"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX]).tap {
            ext.prebid.secondaryBidders = [OPENX]
        }

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder call"
        assert bidder.getBidderRequests(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX.value]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid for openX bidder"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT
    }

    def "PBS shouldn't treat alias as secondary when root bidder is secondary in request config"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([OPENX, ALIAS]).tap {
            it.ext.prebid.aliases = [(ALIAS.value): OPENX]
            ext.prebid.secondaryBidders = [OPENX]
        }

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        and: "Set up openx alias bidder response"
        genericAliasBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBS should process bidder request"
        assert bidder.getBidderRequest(bidRequest.id)
        assert genericAliasBidder.getBidderRequest(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response should contain openX alias and generic"
        assert bidResponse.seatbid.seat.sort() == [ALIAS, GENERIC].sort()

        and: "PBs response should contain response body from generic and alias bidder"
        def httpCalls = bidResponse?.ext?.debug?.httpcalls
        assert httpCalls[GENERIC.value]?.responseBody
        assert httpCalls[ALIAS.value]?.responseBody

        and: "PBS response shouldn't contain response body from openX bidder"
        assert !httpCalls[OPENX.value]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT
    }

    def "PBS should prioritize request secondary bidders config over account config when there conflict"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = getEnrichedBidRequest([GENERIC, OPENX]).tap {
            ext.prebid.secondaryBidders = [OPENX]
        }

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_MILLISECONDS)

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = secondaryBiddersConfig
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder call"
        assert bidder.getBidderRequests(bidRequest.id)
        assert openXBidder.getBidderRequest(bidRequest.id)

        and: "PBs response shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX.value]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid for openX bidder"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        where:
        secondaryBiddersConfig << [
                new AccountAuctionConfig(secondaryBidders: [OPENX]),
                new AccountAuctionConfig(secondaryBiddersSnakeCase: [OPENX])
        ]
    }

    private static BidRequest getEnrichedBidRequest(List<BidderName> bidderNames) {
        BidRequest.defaultBidRequest.tap {
            if (bidderNames.contains(GENERIC)) {
                it.imp[0]?.ext?.prebid?.bidder?.generic = new Generic()
            }
            if (bidderNames.contains(OPENX)) {
                it.imp[0]?.ext?.prebid?.bidder?.openx = Openx.defaultOpenx
            }
            if (bidderNames.contains(ALIAS)) {
                it.imp[0]?.ext?.prebid?.bidder?.alias = new Generic()
            }
            enabledReturnAllBidStatus()
        }
    }
}
