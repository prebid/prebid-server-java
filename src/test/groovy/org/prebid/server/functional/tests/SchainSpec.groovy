package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidSchain
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.request.auction.SourceExt
import org.prebid.server.functional.model.request.auction.SupplyChain
import org.prebid.server.functional.model.request.auction.SupplyChainNode
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.request.auction.Fd.EXCHANGE

class SchainSpec extends BaseSpec {

    private static final GLOBAL_SUPPLY_SCHAIN_NODE = new SupplyChainNode().tap {
        asi = PBSUtils.randomString
        sid = PBSUtils.randomString
        hp = PBSUtils.randomNumber
        rid = PBSUtils.randomString
    }

    private static final Map<String, String> SCHAIN_NODE_CONFIG = ["auction.host-schain-node": encode(GLOBAL_SUPPLY_SCHAIN_NODE)]
    private static final Map<String, String> ORTB_2_5_CONFIG = ["adapters.generic.ortb-version": "2.5"]

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(SCHAIN_NODE_CONFIG)
    @Shared
    PrebidServerService prebidServerServiceWithElderOrtb = pbsServiceFactory.getService(ORTB_2_5_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(SCHAIN_NODE_CONFIG)
        pbsServiceFactory.removeContainer(ORTB_2_5_CONFIG)
    }

    def "Global schain node should be appended when only ext.prebid.schains exists"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain to bidRequest"
        def supplyChain = SupplyChain.defaultSupplyChain
        def prebidSchain = new PrebidSchain(bidders: ["generic"], schain: supplyChain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain?.nodes == supplyChain.nodes + GLOBAL_SUPPLY_SCHAIN_NODE
    }

    def "PBS should copy ext.schain to source.ext.schain when source.ext.schain doesn't exist"() {
        given: "Basic bid request with ext.schain"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.schain = SupplyChain.defaultSupplyChain
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "ext.schain should be appended to the source.ext.schain"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain == bidRequest.ext.schain
    }

    def "PBS should move ext.schain to source.ext.schain when source exists"() {
        given: "Basic bid request with ext.schain"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.schain = SupplyChain.defaultSupplyChain
            source = new Source(fd: EXCHANGE)
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "ext.schain should be appended to the source.ext.schain"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain == bidRequest.ext.schain

        and: "PBS should not override source params from request"
        assert bidderRequest.source?.fd == bidRequest.source.fd
    }

    def "PBS should not move ext.schain to source.ext.schain when source.ext.schain exists"() {
        given: "Basic bid request with ext.schain, source.ext.schain"
        def supplyChain = SupplyChain.defaultSupplyChain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.schain = supplyChain
            source = new Source(ext: new SourceExt(schain: supplyChain))
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain source.ext.schain from request.source.ext.schain"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain == supplyChain
    }

    def "Global schain node should be appended to the end of the node array when only source.ext.schain exists"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default source schain to bidRequest"
        def supplyChain = SupplyChain.defaultSupplyChain
        def sourceExt = new SourceExt(schain: supplyChain)
        bidRequest.source = new Source(ext: sourceExt)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain?.nodes == supplyChain.nodes + GLOBAL_SUPPLY_SCHAIN_NODE
    }

    def "Global schain node should be appended when both ext.prebid.schains and source.ext.schain exist"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain to bidRequest"
        def supplyChain = SupplyChain.defaultSupplyChain
        def prebidSchain = new PrebidSchain(bidders: ["generic"], schain: supplyChain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        and: "Set default source schain to bidRequest"
        def sourceExt = new SourceExt(schain: supplyChain)
        bidRequest.source = new Source(ext: sourceExt)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain?.nodes == supplyChain.nodes + GLOBAL_SUPPLY_SCHAIN_NODE
    }

    def "Global schain node should be appended when ext.prebid.schains and source.ext.schain doesn't exist"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain?.nodes == [GLOBAL_SUPPLY_SCHAIN_NODE]
    }

    def "Global schain node should be appended when ext.prebid.schains applied for unknown bidder"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set default prebid schain with unknown bidder to bidRequest"
        def prebidSchain = new PrebidSchain(bidders: ["appnexus"], schain: SupplyChain.defaultSupplyChain)
        bidRequest.ext.prebid.schains = [prebidSchain]

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Configured schain node should be appended to the end of the node array"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source?.schain?.nodes == [GLOBAL_SUPPLY_SCHAIN_NODE]
    }

    def "PBS should use source.ext.schain when ext.prebid.schains.bidder isn't requested"() {
        given: "Default basic BidRequest with schain obj"
        def supplyChain = SupplyChain.defaultSupplyChain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            source = new Source(ext: new SourceExt(schain: supplyChain))
            ext.prebid.schains = [new PrebidSchain(bidders: [UNKNOWN.value], schain: SupplyChain.defaultSupplyChain)]
        }

        when: "PBS processes auction request"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested source.ext.schain"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source.ext.schain == supplyChain
        assert !bidderRequest.source.schain
    }

    def "PBS should use ext.prebid.schains.schain instead of source.ext.chain when ext.prebid.schains.bidder is requested"() {
        given: "Default basic BidRequest with schain obj"
        def supplyChain = SupplyChain.defaultSupplyChain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            source = new Source(ext: new SourceExt(schain: SupplyChain.defaultSupplyChain))
            ext.prebid.schains = [new PrebidSchain(bidders: [GENERIC.value], schain: supplyChain)]
        }

        when: "PBS processes auction request"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain requested ext.prebid.schains[*].schain"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.source.ext.schain == supplyChain
        assert !bidderRequest.source.schain
    }
}
