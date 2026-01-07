package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_TIMED_OUT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SecondaryBidderSpec extends BaseSpec {

    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.openx.enabled" : "true",
            "adapters.openx.endpoint": "$networkServiceContainer.rootUri/openx-auction".toString()]

    private static final Map<String, String> OPENX_ALIAS_CONFIG = [
            "adapters.${OPENX.value}.aliases.${OPENX_ALIAS}.enabled" : "true",
            "adapters.${OPENX.value}.aliases.${OPENX_ALIAS}.endpoint": "$networkServiceContainer.rootUri/openx-alias-auction".toString()]

    protected static final Bidder openXBidder = new Bidder(networkServiceContainer, "/openx-auction")
    protected static final Bidder openXAliasBidder = new Bidder(networkServiceContainer, "/openx-alias-auction")

    @Shared
    PrebidServerService pbsServiceWithOpenXAndIXBidder = pbsServiceFactory.getService(OPENX_CONFIG + OPENX_ALIAS_CONFIG)

    @Override
    def cleanupSpec() {
        openXBidder.setResponse()
        openXAliasBidder.setResponse()
        pbsServiceFactory.removeContainer(OPENX_CONFIG + OPENX_ALIAS_CONFIG)
    }

    def "PBS should proceed as default when secondaryBidders not define in config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: null)
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bit"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS should emit a warning when null in secondary bidders config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [null])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS shouldn't emit a warning when invalid bidder in secondary bidders config"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [UNKNOWN])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS should thread all bidders as primary when all requested bidders in secondary bidders config"() {
        given: "Default basic BidRequest with generic and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.ext.prebid.returnAllBidStatus = true
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
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed generic request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1

        and: "PBs should processed openx request"
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert openXBidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid

        cleanup:
        openXBidder.reset()
    }

    def "PBS shouldn't wait on non prioritize bidder when primary bidder respond"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDilay(5)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

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
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == ["secondary bidder timed out, auction proceeded"]

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
                it.openxAlias = Openx.defaultOpenx
            }
            it.ext.prebid.aliases = [(OPENX_ALIAS.value): OPENX]
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDilay(5)

        and: "Set up openx alias bidder response"
        openXAliasBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXAliasBidderRequests = openXAliasBidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1
        assert openXAliasBidderRequests.size() == 1

        and: "PBs repose shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX]?.responseBody

        and: "PBS shouldn't contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx"
        assert bidResponse.ext?.warnings[ErrorType.OPENX].message == ["secondary bidder timed out, auction proceeded"]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        cleanup: "Reset mock"
        openXBidder.reset()
        openXAliasBidder.reset()
    }

    def "PBS shouldn't wait on secondary bidder when alias bidder respond with dilay"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.tap {
                it.openx = Openx.defaultOpenx
                it.openxAlias = Openx.defaultOpenx
            }
            it.ext.prebid.aliases = [(OPENX_ALIAS.value): OPENX]
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [OPENX_ALIAS])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXAliasBidder.setResponseWithDilay(5)

        and: "Set up openx alias bidder response"
        openXBidder.setResponse()

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXAliasBidderRequests = openXAliasBidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1
        assert openXAliasBidderRequests.size() == 1

        and: "PBs repose shouldn't contain response body from openX bidder"
        assert !bidResponse?.ext?.debug?.httpcalls[OPENX_ALIAS]?.responseBody

        and: "PBS should contain error for openX due to timeout"
        assert !bidResponse.ext?.errors

        and: "PBs should respond with warning for openx alias"
        assert bidResponse.ext?.warnings[ErrorType.OPENX_ALIAS].message == ["secondary bidder timed out, auction proceeded"]

        and: "PBs should populate seatNonBid"
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX_ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == ERROR_TIMED_OUT

        cleanup: "Reset mock"
        openXBidder.reset()
        openXAliasBidder.reset()
    }

    def "PBS should pass auction as usual when secondary bidder respond first and primary with dilay"() {
        given: "Default bid request with generic and openX bidders"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Account in the DB"
        def accountConfig = AccountConfig.defaultAccountConfig.tap {
            it.auction = new AccountAuctionConfig(secondaryBidders: [GENERIC])
        }
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Set up openx bidder response with delay"
        openXBidder.setResponseWithDilay(1)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithOpenXAndIXBidder.sendAuctionRequest(bidRequest)

        then: "PBs should processed bidder request"
        def genericBidderRequests = bidder.getBidderRequests(bidRequest.id)
        def openXBidderRequests = openXBidder.getBidderRequests(bidRequest.id)
        assert genericBidderRequests.size() == 1
        assert openXBidderRequests.size() == 1

        and: "PBS shouldn't contain errors, warnings and seat non bid"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors
        assert !bidResponse.ext.seatnonbid

        cleanup: "Reset mock"
        openXBidder.reset()
        bidder.reset()
    }
}
