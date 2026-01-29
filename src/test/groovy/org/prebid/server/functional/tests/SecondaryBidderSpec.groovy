package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_TIMED_OUT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SecondaryBidderSpec extends BaseSpec {

    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.${OPENX.value}.enabled" : "true",
            "adapters.${OPENX.value}.endpoint": "$networkServiceContainer.rootUri/openx-auction".toString()]

    private static final Map<String, String> GENERIC_ALIAS_CONFIG = [
            "adapters.${GENERIC.value}.aliases.${ALIAS}.enabled" : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS}.endpoint": "$networkServiceContainer.rootUri/generic-alias-auction".toString()]
    private static final String WARNING_TIME_OUT_MESSAGE = "secondary bidder timed out, auction proceeded"
    private static final Integer RESPONSE_DELAY_SECONDS = 5
    private static final Bidder openXBidder = new Bidder(networkServiceContainer, "/openx-auction")
    private static final Bidder genericAliasBidder = new Bidder(networkServiceContainer, "/generic-alias-auction")

    @Shared
    PrebidServerService pbsServiceWithOpenXBidder = pbsServiceFactory.getService(OPENX_CONFIG + GENERIC_ALIAS_CONFIG)

    @Override
    def cleanupSpec() {
        openXBidder.reset()
        genericAliasBidder.reset()
        pbsServiceFactory.removeContainer(OPENX_CONFIG + GENERIC_ALIAS_CONFIG)
    }

    def "PBS should proceed as default when secondaryBidders not define in config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: null)
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bit"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS shouldn't emit a warning when empty secondary bidders config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [null])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS shouldn't emit a warning when invalid bidder in secondary bidders config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [UNKNOWN])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS should thread all bidders as primary when all requested bidders in secondary bidders config"() {
        given: "Default basic BidRequest with generic and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            enabledReturnAllBidStatus()
        }

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

        cleanup:
        openXBidder.reset()
    }

    def "PBS shouldn't wait on non prioritize bidder when primary bidder respond"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_SECONDS)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder call"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1

        and: "PBs response shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX]?.responseBody

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

    def "PBS shouldn't treated alias bidder as secondary when root bidder code in secondary"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.tap {
                it.openx = Openx.defaultOpenx
                it.alias = new Generic()
            }
            it.ext.prebid.aliases = [(ALIAS.value): OPENX]
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDelay(RESPONSE_DELAY_SECONDS)

        and: "Set up openx alias bidder response"
        genericAliasBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXAliasBidderRequests = genericAliasBidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1
        assert openXAliasBidderRequests.size() == 1

        and: "PBs response should contain openX alias and generic"
        assert bidResponse.seatbid.seat == [ALIAS, GENERIC]

        and: "PBs repose shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        cleanup: "Reset mock"
        openXBidder.reset()
        genericAliasBidder.reset()
    }

    def "PBS shouldn't wait on secondary bidder when alias bidder respond with delay"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.tap {
                it.openx = Openx.defaultOpenx
                it.alias = new Generic()
            }
            it.ext.prebid.aliases = [(ALIAS.value): OPENX]
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [ALIAS])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        genericAliasBidder.setResponseWithDelay(RESPONSE_DELAY_SECONDS)

        and: "Set up openx alias bidder response"
        openXBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXAliasBidderRequests = genericAliasBidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1
        assert openXAliasBidderRequests.size() == 1

        and: "PBs repose shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[ALIAS]?.responseBody

        and: "PBS should contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx alias"
        assert bidResponse.ext?.warnings[ErrorType.ALIAS].message == [WARNING_TIME_OUT_MESSAGE]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        cleanup: "Reset mock"
        genericAliasBidder.reset()
    }

    def "PBS should pass auction as usual when secondary bidder respond first and primary with delay"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [GENERIC])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.reset()
        openXBidder.setResponseWithDelay(2)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1

        and: "PBs response should contain openX alias and generic"
        assert bidResponse.seatbid.seat.sort() == [OPENX, GENERIC].sort()

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }

    def "PBS should pass auction as usual when secondary bidder respond all primary bidders"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            enabledReturnAllBidStatus()
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [GENERIC, OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1

        and: "PBs response should contain openX alias and generic"
        assert bidResponse.seatbid.seat.sort() == [OPENX, GENERIC].sort()

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext?.seatnonbid
    }
}
