package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.vtrack.VtrackRequest
import org.prebid.server.functional.model.request.vtrack.xml.Vast
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

// TODO: move metrics tests to the respective specifications as the metrics are a part of normal PBS operation
// TODO: this won't work as is for banner type as we need to signal PBS to store bid in the cache
@PBSTest
class MetricsSpec extends BaseSpec {

    def setup() {
        // flushing PBS metrics by receiving collected metrics so that each new test works with a fresh state
        defaultPbsService.sendCollectedMetricsRequest()
    }

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

    @PendingFeature
    def "PBS should update prebid_cache.creative_size.json metric when json creative is received"() {
        given: "Current value of metric prebid_cache.requests.ok"
        def initialValue = getCurrentMetricValue("prebid_cache.requests.ok")

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.enableCache()

        and: "Default basic bid with banner creative"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def adm = PBSUtils.randomString
        bidResponse.seatbid[0].bid[0].adm = adm

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes amp request"
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

    def "PBS should increase request_time metric when auction was held"() {
        given: "Current value of metric request_time"
        def initialValue = getCurrentMetricValue("request_time")

        and: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        and: "PBS processes collected metrics request"
        def response = defaultPbsService.sendCollectedMetricsRequest()

        then: "Response should contain metrics"
        assert response.size() > 0

        and: "request_time metric should be increased"
        assert response["request_time"] == initialValue + 1
    }

    private static int getCurrentMetricValue(String name) {
        def response = defaultPbsService.sendCollectedMetricsRequest()
        response[name] as Integer ?: 0
    }
}
