package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest
import spock.lang.Unroll

import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AuctionSpec extends BaseSpec {

    private static final String PBS_VERSION_HEADER = "pbs-java/$PBS_VERSION"

    @Unroll
    def "PBS should return version in response header for auction request for #description"() {

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == PBS_VERSION_HEADER

        where:
        bidRequest                   || description
        BidRequest.defaultBidRequest || "valid bid request"
        new BidRequest()             || "invalid bid request"
    }
}
