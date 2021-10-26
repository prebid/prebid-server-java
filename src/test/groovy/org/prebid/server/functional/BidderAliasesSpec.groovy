package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

class BidderAliasesSpec extends BaseSpec {

    def "PBS should support bidder aliases"() {
        given: "Default BidRequest with aliases"
        def alias = "genericAlias"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(alias): GENERIC]
            imp[0].ext.prebid.bidder.genericAlias = imp[0].ext.prebid.bidder.generic
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call bidder twice"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2
        assert bidderRequests[0].ext.prebid.aliases == bidderRequests[1].ext.prebid.aliases
    }
}
