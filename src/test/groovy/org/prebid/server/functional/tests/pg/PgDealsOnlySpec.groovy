package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class PgDealsOnlySpec extends BasePgSpec {

    def "PBS shouldn't call bidder when bidder pgdealsonly flag is set to true and no available PG line items"() {
        given: "Bid request with set pgdealsonly flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(pgDealsOnly: true)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(new PlansResponse(lineItems: []))

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't called bidder during auction"
        assert initialBidderRequestCount == bidder.requestCount

        and: "PBS returns a not calling bidder warning"
        def auctionWarnings = auctionResponse.ext?.warnings?.get(PREBID)
        assert auctionWarnings.size() == 1
        assert auctionWarnings[0].code == 999
        assert auctionWarnings[0].message == "Not calling $GENERIC.value bidders for impression ${bidRequest.imp[0].id}" +
                " due to pgdealsonly flag and no available PG line items."
    }

    def "PBS shouldn't call bidder when bidder alias is set, bidder pgdealsonly flag is set to true and no available PG line items"() {
        given: "Bid request with set bidder alias and pgdealsonly flag"
        def bidderAliasName = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            def prebid = new Prebid(aliases: [(bidderAliasName): BidderName.GENERIC], debug: 1)
            ext = new BidRequestExt(prebid: prebid)
        }
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(pgDealsOnly: true)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(new PlansResponse(lineItems: []))

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS hasn't called bidder during auction"
        assert initialBidderRequestCount == bidder.requestCount

        and: "PBS returns a not calling bidder warning"
        def auctionWarnings = auctionResponse.ext?.warnings?.get(PREBID)
        assert auctionWarnings.size() == 1
        assert auctionWarnings[0].code == 999
        assert auctionWarnings[0].message == "Not calling $GENERIC.value bidders for impression ${bidRequest.imp[0].id}" +
                " due to pgdealsonly flag and no available PG line items."
    }

    def "PBS should call bidder when bidder pgdealsonly flag is set to false and no available PG line items"() {
        given: "Bid request with set pgdealsonly flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(pgDealsOnly: false)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(new PlansResponse(lineItems: []))

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "PBS has called bidder during auction"
        assert initialBidderRequestCount + 1 == bidder.requestCount
        assert !auctionResponse.ext?.warnings
    }

    def "PBS should return an error when bidder dealsonly flag is set to true, no available PG line items and bid response misses 'dealid' field"() {
        given: "Bid request with set dealsonly flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(dealsOnly: true)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id))

        and: "Bid response with missing 'dealid' field"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid[0].dealid = null
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder was requested"
        assert initialBidderRequestCount + 1 == bidder.requestCount

        and: "PBS returns an error of missing 'dealid' field in bid"
        def bidErrors = auctionResponse.ext?.errors?.get(GENERIC)
        def bidId = bidResponse.seatbid[0].bid[0].id

        assert bidErrors?.size() == 1
        assert bidErrors[0].code == 5
        assert bidErrors[0].message ==~ /BidId `$bidId` validation messages:.* Error: / +
                /Bid "$bidId" missing required field 'dealid'.*/
    }

    def "PBS should add dealsonly flag when it is not specified and pgdealsonly flag is set to true"() {
        given: "Bid request with set pgdealsonly flag, dealsonly is not specified"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(pgDealsOnly: true)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id))

        and: "Bid response with missing 'dealid' field to check dealsonly flag was added and worked"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid[0].dealid = null
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder was requested"
        assert initialBidderRequestCount + 1 == bidder.requestCount

        and: "PBS added dealsonly flag to the bidder request"
        assert auctionResponse.ext?.debug?.resolvedRequest?.imp?.first()?.ext?.prebid?.bidder?.generic?.dealsOnly

        and: "PBS returns an error of missing 'dealid' field in bid"
        def bidErrors = auctionResponse.ext?.errors?.get(GENERIC)
        def bidId = bidResponse.seatbid[0].bid[0].id

        assert bidErrors?.size() == 1
        assert bidErrors[0].code == 5
        assert bidErrors[0].message ==~ /BidId `$bidId` validation messages:.* Error: / +
                /Bid "$bidId" missing required field 'dealid'.*/
    }

    def "PBS shouldn't return an error when bidder dealsonly flag is set to true, no available PG line items and bid response misses 'dealid' field"() {
        given: "Bid request with set dealsonly flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp.first().ext.prebid.bidder.generic = new Generic(dealsOnly: false)
        def initialBidderRequestCount = bidder.requestCount

        and: "No line items response"
        generalPlanner.initPlansResponse(PlansResponse.getDefaultPlansResponse(bidRequest.site.publisher.id))

        and: "Bid response with missing 'dealid' field"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid[0].dealid = null
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        when: "Auction is happened"
        def auctionResponse = pgPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder was requested"
        assert initialBidderRequestCount + 1 == bidder.requestCount

        and: "PBS hasn't returned an error"
        !auctionResponse.ext?.errors
    }
}
