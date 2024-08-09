package org.prebid.server.functional.tests.module.ortb2blocking

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.Ortb2BlockingAttribute
import org.prebid.server.functional.model.config.Ortb2BlockingAttributes
import org.prebid.server.functional.model.config.Ortb2BlockingConfig
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.BIDDER_REQUEST
import static org.prebid.server.functional.model.config.Stage.RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_ADVERTISER_BLOCKED
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class Ortb2BlockingSpec extends ModuleBaseSpec {

    private final PrebidServerService pbsServiceWithEnabledOrtb2Blocking = pbsServiceFactory.getService(ortb2BlockingSettings)

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with rejected advertiser blocked status code"() {
        given: "Default account with return bid status"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response with aDomain"
        def aDomain = PBSUtils.randomString
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid.first.bid.first.adomain = [aDomain]
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB with blocking configuration"
        def blockingAttributes = new Ortb2BlockingAttributes(badv: new Ortb2BlockingAttribute(enforceBlocks: true, blockedAdomain: [aDomain]))
        def blockingConfig = new Ortb2BlockingConfig(attributes: blockingAttributes)
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [BIDDER_REQUEST, RAW_BIDDER_RESPONSE])
        def richMediaFilterConfig = new PbsModulesConfig(ortb2Blocking: blockingConfig)
        def accountHooksConfig = new AccountHooksConfiguration(executionPlan: executionPlan, modules: richMediaFilterConfig)
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes the auction request"
        def response = pbsServiceWithEnabledOrtb2Blocking.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for the called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_ADVERTISER_BLOCKED
    }
}
