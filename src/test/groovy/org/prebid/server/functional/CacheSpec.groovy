package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

@PBSTest
class CacheSpec extends BaseSpec {

    def "PBS should update prebid_cache.creative_size.xml metric when xml creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue("prebid_cache.requests.ok")

        and: "Default VtrackRequest"
        def accountId = PBSUtils.randomNumber.toString()
        def creative = mapper.encodeXml(Vast.getDefaultVastModel(PBSUtils.randomString))
        def request = VtrackRequest.getDefaultVtrackRequest(creative)

        when: "PBS processes vtrack request"
        defaultPbsService.sendVtrackRequest(request, accountId)

        then: "prebid_cache.creative_size.xml metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        def creativeSize = creative.bytes.length
        assert metrics["prebid_cache.creative_size.xml"] == creativeSize
        assert metrics["prebid_cache.requests.ok"] == initialValue + 1

        and: "account.<account-id>.prebid_cache.creative_size.xml should be updated"
        assert metrics["account.${accountId}.prebid_cache.creative_size.xml" as String] == creativeSize
        assert metrics["account.${accountId}.prebid_cache.requests.ok" as String] == 1
    }

    def "PBS should update prebid_cache.creative_size.json metric when json creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue("prebid_cache.requests.ok")

        and: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def adm = PBSUtils.randomString
        bidResponse.seatbid[0].bid[0].adm = adm

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()

        then: "prebid_cache.creative_size.json should be update"
        def creativeSize = adm.bytes.length
        assert metrics["prebid_cache.creative_size.json"] == creativeSize
        assert metrics["prebid_cache.requests.ok"] == initialValue + 1

        and: "account.<account-id>.prebid_cache.creative_size.json should be update"
        def accountId = bidRequest.site.publisher.id
        assert metrics["account.${accountId}.prebid_cache.requests.ok" as String] == 1
    }

    def "PBS should cache bids when targeting is specified"() {
        given: "Default BidRequest with cache, targeting"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = new Targeting()

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 1
    }

    def "PBS should not cache bids when targeting isn't specified"() {
        given: "Default BidRequest with cache"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()
        bidRequest.ext.prebid.targeting = null

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not call PBC"
        assert prebidCache.getRequestCount(bidRequest.imp[0].id) == 0
    }
}
