package org.prebid.server.functional.tests.bidder.openx

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.OpenxBidResponse
import org.prebid.server.functional.model.response.auction.OpenxBidResponseExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class OpenxSpec extends BaseSpec {

    private static final Map OPENX_CONFIG = ["adapters.openx.enabled" : "true",
                                             "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()]

    @Shared
    PrebidServerService pbsService = pbsServiceFactory.getService(OPENX_CONFIG)

    def "PBS should populate fledge config when bid response with fledge and imp[0].ext.ae = 1"() {
        given: "Default basic BidRequest with ae and openx bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = 1
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response with fledge config"
        def impId = bidRequest.imp[0].id
        def fledgeConfig = [(PBSUtils.randomString): PBSUtils.randomString]
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): fledgeConfig]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain fledge config"
        def auctionConfigs = response.ext?.prebid?.fledge?.auctionConfigs
        assert auctionConfigs?.size() == 1
        assert auctionConfigs[0].impId == impId
        assert auctionConfigs[0].bidder == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].adapter == bidResponse.seatbid[0].seat.value
        assert auctionConfigs[0].config == fledgeConfig
    }

    def "PBS shouldn't populate fledge config when imp[0].ext.ae = 0"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = 0
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response"
        def impId = bidRequest.imp[0].id
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = [(impId): [(PBSUtils.randomString): PBSUtils.randomString]]
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext.prebid.fledge
    }

    def "PBS shouldn't populate fledge config when imp[0].ext.ae = 1 and bid response didn't return fledge config"() {
        given: "Default basic BidRequest without ae"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.ae = 1
            imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
        }

        and: "Default bid response"
        def bidResponse = OpenxBidResponse.getDefaultBidResponse(bidRequest).tap {
            ext = new OpenxBidResponseExt().tap {
                fledgeAuctionConfigs = null
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain fledge config"
        assert !response.ext.prebid.fledge
    }
}
