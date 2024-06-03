package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidCacheSettings
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

class BidExpResponseSpec extends BaseSpec {

    private static def hostBannerTtl = PBSUtils.randomNumber
    private static def hostVideoTtl = PBSUtils.randomNumber
    private static def cacheTtlService = pbsServiceFactory.getService(['cache.banner-ttl-seconds': hostBannerTtl as String,
                                                                       'cache.video-ttl-seconds' : hostVideoTtl as String])

    def "PBS auction should resolve bid.exp from response that is set by the bidder’s adapter"() {
        given: "Default basic bid with exp"
        def bidResponseExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = bidResponseExp
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidResponseExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from request.imp[].exp when it have value"() {
        given: "Default basic bid with exp"
        def bidRequestExp = PBSUtils.randomNumber
        bidRequest.tap {
            imp.first.exp = bidRequestExp
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from request.ext.prebid.cache.bids when it have value"() {
        given: "Default basic bid with ext.prebid.cache.bids"
        def bidRequestExp = PBSUtils.randomNumber
        bidRequest.tap {
            enableCache()
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestExp)
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from request.ext.prebid.cache.vastxml when it have value"() {
        given: "Default basic bid with ext.prebid.cache.bids"
        def bidRequestExp = PBSUtils.randomNumber
        bidRequest.tap {
            enableCache()
            ext.prebid.cache.vastXml = new PrebidCacheSettings(ttlSeconds: bidRequestExp)
        }

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should resolve exp from account config for banner request when it have value"() {
        given: "default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }

    def "PBS auction should resolve exp from account config for video request when it have value"() {
        given: "default bidRequest"
        def bidRequest = BidRequest.defaultVideoRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(videoCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }

    def "PBS auction shouldn't resolve exp from account config when bidRequest type doesn't matching"() {
        given: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain exp data"
        assert !response.seatbid.first.bid.first.exp

        where:
        bidRequest                     | auctionConfig
        BidRequest.defaultBidRequest   | new AccountAuctionConfig(videoCacheTtl: PBSUtils.randomNumber)
        BidRequest.defaultVideoRequest | new AccountAuctionConfig(bannerCacheTtl: PBSUtils.randomNumber)
    }

    def "PBS auction should resolve exp from global banner config for banner request"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = cacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [hostBannerTtl]
    }

    def "PBS auction should resolve exp from global video config for banner request"() {
        given: "Default bidRequest"
        def bidRequest = BidRequest.defaultVideoRequest

        and: "Set bidder response without exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = cacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [hostVideoTtl]
    }

    def "PBS auction should prioritize value from bid.exp rather than request.imp[].exp"() {
        given: "Default basic bidRequest with exp"
        bidRequest.tap {
            imp.first.exp = PBSUtils.randomNumber
        }

        and: "Set bidder response with exp"
        def bidResponseExp = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].exp = bidResponseExp
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidResponseExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should prioritize value from request.imp[].exp rather than request.ext.prebid.cache"() {
        given: "Default basic bidRequest with exp"
        def bidRequestExp = PBSUtils.randomNumber
        def bidRequestBidsCacheExp = PBSUtils.randomNumber
        bidRequest.tap {
            enableCache()
            imp.first.exp = bidRequestExp
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestBidsCacheExp)
        }

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestExp]

        where:
        bidRequest << [BidRequest.defaultBidRequest, BidRequest.defaultVideoRequest]
    }

    def "PBS auction should prioritize value from request.ext.prebid.cache rather than account config"() {
        given: "Default basic bidRequest with exp"
        def bidRequestBidsCacheExp = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            enableCache()
            ext.prebid.cache.bids = new PrebidCacheSettings(ttlSeconds: bidRequestBidsCacheExp)
        }

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [bidRequestBidsCacheExp]
    }

    def "PBS auction should prioritize value from account config rather than host config"() {
        given: "Default basic bidRequest with exp"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountCacheTtl = PBSUtils.randomNumber
        def auctionConfig = new AccountAuctionConfig(bannerCacheTtl: accountCacheTtl)
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(auction: auctionConfig))
        accountDao.save(account)

        and: "Set bidder response with exp"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = cacheTtlService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain exp data"
        assert response.seatbid.bid.first.exp == [accountCacheTtl]
    }
}
