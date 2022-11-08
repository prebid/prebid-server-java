package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidCache
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.mockserver.model.HttpStatusCode.BAD_REQUEST_400
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class TargetingSpec extends BaseSpec {

    def "PBS should include targeting bidder specific keys when alwaysIncludeDeals is true and deal bid wins"() {
        given: "Bid request with alwaysIncludeDeals = true"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, alwaysIncludeDeals: true)
        }

        and: "Bid response with 2 bids where deal bid has higher price"
        def bidPrice = PBSUtils.randomPrice
        def dealBidPrice = bidPrice + 1
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp[0]).tap { it.price = bidPrice }
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
            seatbid[0].bid[0].price = dealBidPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)
        def bidderName = GENERIC.value

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        def notBidderKeys = targetingKeyMap.findAll { !it.key.endsWith(bidderName) }
        notBidderKeys.each { assert targetingKeyMap.containsKey("${it.key}_$bidderName" as String) }
    }

    def "PBS should not include targeting bidder specific keys when alwaysIncludeDeals flag is #condition"() {
        given: "Bid request with set alwaysIncludeDeals flag"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, alwaysIncludeDeals: alwaysIncludeDeals)
        }

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp[0]).tap { it.price = bidPrice }
            seatbid[0].bid[0].dealid = PBSUtils.randomNumber
            seatbid[0].bid[0].price = dealBidPrice
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        targetingKeyMap.each { assert !it.key.endsWith(GENERIC.value) }

        where:
        condition                 || bidPrice                       || dealBidPrice   || alwaysIncludeDeals
        "false and deal bid wins" || PBSUtils.getRandomPrice(1, 10) || bidPrice + 0.5 || false
        "true and deal bid loses" || PBSUtils.getRandomPrice(1, 10) || bidPrice - 0.5 || true
    }

    def "PBS should not include bidder specific keys in bid response targeting when includeBidderKeys is #includeBidderKeys and cache.winningOnly is #winningOnly"() {
        given: "Bid request with set includeBidderKeys, winningOnly flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys)
            it.ext.prebid.cache = new PrebidCache(winningOnly: winningOnly)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = getEnabledWinBidsPbsService().sendAuctionRequest(bidRequest)

        then: "PBS response targeting does not contain bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        targetingKeyMap.each { assert !it.key.endsWith(GENERIC.value) }

        where:
        includeBidderKeys || winningOnly
        false             || null
        null              || true
        false             || false
        null              || null
    }

    def "PBS should include bidder specific keys in bid response targeting when includeBidderKeys is #includeBidderKeys and cache.winningOnly is #winningOnly"() {
        given: "Bid request with set includeBidderKeys, winningOnly flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys)
            it.ext.prebid.cache = new PrebidCache(winningOnly: winningOnly)
        }

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidResponse)
        def bidderName = GENERIC.value

        when: "PBS processes auction request"
        def response = getDisabledWinBidsPbsService().sendAuctionRequest(bidRequest)

        then: "PBS response targeting contains bidder specific keys"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        def notBidderKeys = targetingKeyMap.findAll { !it.key.endsWith(bidderName) }
        notBidderKeys.each { assert targetingKeyMap.containsKey("${it.key}_$bidderName" as String) }

        where:
        includeBidderKeys || winningOnly
        true              || null
        null              || false
        true              || false
        null              || null
    }

    def "PBS should throw an exception when targeting includeBidderKeys and includeWinners flags are false"() {
        given: "Bid request with includeBidderKeys = false and includeWinners = false"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: false, includeWinners: false)
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS returns an error"
        def exception = thrown PrebidServerException
        verifyAll(exception) {
            it.statusCode == BAD_REQUEST_400.code()
            it.responseBody == "Invalid request format: ext.prebid.targeting: At least one of includewinners or " +
                    "includebidderkeys must be enabled to enable targeting support"
        }
    }

    def "PBS should include only #presentDealKey deal specific targeting key when includeBidderKeys is #includeBidderKeys and includeWinners is #includeWinners"() {
        given: "Bid request with set includeBidderKeys and includeWinners flags"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.targeting = new Targeting(includeBidderKeys: includeBidderKeys, includeWinners: includeWinners)
        }

        and: "Deal specific bid response"
        def dealId = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].dealid = dealId
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response targeting includes only one deal specific key"
        def targetingKeyMap = response.seatbid?.first()?.bid?.first()?.ext?.prebid?.targeting
        assert targetingKeyMap
        assert !targetingKeyMap.containsKey(absentDealKey)

        def dealTargetingKey = targetingKeyMap.get(presentDealKey)
        assert dealTargetingKey
        assert dealTargetingKey == dealId as String

        where:
        includeBidderKeys || includeWinners || absentDealKey              || presentDealKey
        false             || true           || "hb_deal_" + GENERIC.value || "hb_deal"
        true              || false          || "hb_deal"                  || "hb_deal_" + GENERIC.value
    }

    private PrebidServerService getEnabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "true"])
    }

    private PrebidServerService getDisabledWinBidsPbsService() {
        pbsServiceFactory.getService(["auction.cache.only-winning-bids": "false"])
    }
}
