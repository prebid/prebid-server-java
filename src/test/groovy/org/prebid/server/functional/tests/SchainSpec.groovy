package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidSchain
import org.prebid.server.functional.model.request.auction.Schain
import org.prebid.server.functional.model.request.auction.SchainNode
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.request.auction.SourceExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

@PBSTest
class SchainSpec extends BaseSpec {

    private static final GLOBAL_SCHAIN_NODE = new SchainNode().tap {
        asi = "pbshostcompany.com"
        sid = "00001"
        hp = 1
        rid = "BidRequest"
    }

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.host-schain-node": mapper.encode(GLOBAL_SCHAIN_NODE)])

    def "Global schain node should be appended when only ext.prebid.schains exists"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain to bidRequest"
        def schain = defaultSchain
        def prebidSchain = new PrebidSchain(bidders: ["generic"], schain: schain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.ext?.schain?.nodes == schain.nodes + GLOBAL_SCHAIN_NODE
    }

    def "Global schain node should be appended to the end of the node array when only source.ext.schain exists"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default source schain to bidRequest"
        def schain = defaultSchain
        def sourceExt = new SourceExt(schain: schain)
        bidRequest.source = new Source(ext: sourceExt)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.ext?.schain?.nodes == schain.nodes + GLOBAL_SCHAIN_NODE
    }

    def "Global schain node should be appended when both ext.prebid.schains and source.ext.schain exist"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain to bidRequest"
        def schain = defaultSchain
        def prebidSchain = new PrebidSchain(bidders: ["generic"], schain: schain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        and: "Set default source schain to bidRequest"
        def sourceSchain = defaultSchain
        def sourceExt = new SourceExt(schain: sourceSchain)
        bidRequest.source = new Source(ext: sourceExt)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.ext?.schain?.nodes == schain.nodes + GLOBAL_SCHAIN_NODE
    }

    def "Global schain node should be appended when ext.prebid.schains and source.ext.schain doesn't exist"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.ext?.schain?.nodes == [GLOBAL_SCHAIN_NODE]
    }

    def "Global schain node should be appended when ext.prebid.schains applied for unknown bidder"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain with unknown bidder to bidRequest"
        def schain = defaultSchain
        def prebidSchain = new PrebidSchain(bidders: ["appnexus"], schain: schain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.ext?.schain?.nodes == [GLOBAL_SCHAIN_NODE]
    }

    private static Schain getDefaultSchain() {
        def node = new SchainNode().tap {
            asi = PBSUtils.randomString
            sid = PBSUtils.randomString
            hp = PBSUtils.randomNumber
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
        }
        new Schain(ver: "1.0", complete: 1, nodes: [node])
    }
}


