package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.auction.AccountAuctionConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class MultibidSpec extends BaseSpec {

    def "PBS should not return seatbid[].bid[].ext.prebid.targeting for non-winning bid in multi-bid response when includeBidderKeys = false"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = false"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(includeBidderKeys: false)

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: GENERIC, maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def anotherBid = Bid.getDefaultBid(bidRequest.imp.first()).tap {
            price = bidResponse.seatbid.first().bid.first().price - 0.1
        }
        bidResponse.seatbid.first().bid << anotherBid

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not return targeting for non-winning bid"
        assert !response.seatbid?.first()?.bid?.last()?.ext?.prebid?.targeting
    }

    def "PBS should return seatbid[].bid[].ext.prebid.targeting for non-winning bid in multi-bid response when includeBidderKeys = true"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(includeBidderKeys: true)

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: GENERIC, maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def anotherBid = Bid.getDefaultBid(bidRequest.imp.first()).tap {
            price = bidResponse.seatbid.first().bid.first().price - 0.1
        }
        bidResponse.seatbid.first().bid << anotherBid

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return targeting for non-winning bid"
        assert response.seatbid?.first()?.bid?.last()?.ext?.prebid?.targeting
    }

    def "PBS should prefer bidRequest over account level config"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = false, alwaysIncludeDeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = requestTargeting
        }

        and: "Account in the DB with different targeting settings"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(targeting: accountTargeting))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should use bidRequest level targeting settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.targeting == bidRequest.ext.prebid.targeting

        and: "PBS should not use account level targeting settings"
        assert bidderRequest.ext.prebid.targeting != accountTargeting

        where:
        requestTargeting                          | accountTargeting
        Targeting.createWithAllValuesSetTo(true)  | Targeting.createWithAllValuesSetTo(false)
        Targeting.createWithAllValuesSetTo(false) | Targeting.createWithAllValuesSetTo(true)
    }

    def "PBS should use account level config when bidRequest does not have targeting settings"() {
        given: "Default basic BidRequest with generic bidder without targeting settings"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.targeting = null
        }

        and: "Account in the DB with targeting settings"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(targeting: accountTargeting))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should use account level targeting settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.targeting == account.config.auction.targeting

        where:
        accountTargeting << [Targeting.createWithAllValuesSetTo(false),
                             Targeting.createWithAllValuesSetTo(true),
                             Targeting.createWithRandomValues()]
    }
}
