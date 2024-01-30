package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.RegsDsa
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidExtDsa
import org.prebid.server.functional.model.response.auction.BidResponse

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class DsaSpec extends BaseSpec {

    def "PBS should populate AMP bid request with DSA regs and send DSA to bidder and succeed when DSA is not required"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)

        and: "Stored default bid request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = bidExt
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        where:
        dsaRequired || bidExt
        0           || null
        1           || new BidExt("dsa": null)
    }

    def "PBS should populate AMP bid request with DSA regs and send DSA to bidder and always succeed when bidder returns DSA"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)

        and: "Stored default bid request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = new BidExt("dsa": BidExtDsa.getDefaultBidExtDsa())
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        where:
        dsaRequired << [0, 1, 2, 3]
    }

    def "PBS should populate AMP bid request with DSA regs and send DSA to bidder and fail on response when DSA is required and bidder does not return DSA"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)

        and: "Stored default bid request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = bidExt
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        and: "Response should contain error"
        def expectedBidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `$expectedBidId` validation messages: Error: Bid \"$expectedBidId\" missing DSA"]

        where:
        dsaRequired || bidExt
        2           || new BidExt("dsa": null)
        3           || null
    }

    def "PBS should send DSA to bidder and succeeds when DSA is not required and bidder does not return DSA"() {
        given: "Default bid request with DSA"
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt("dsa": null)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        and: "DSA is not returned"
        assert response.seatbid[0].bid[0].ext.dsa == null

        where:
        dsaRequired << [0, 1]
    }

    def "PBS should send DSA to bidder and always succeed when bidder returns DSA"() {
        given: "Default bid request with DSA"
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        and: "Default bidder response with DSA"
        def bidDsa = BidExtDsa.getDefaultBidExtDsa()
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt("dsa": bidDsa)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        and: "DSA is not returned"
        def actualDsa = response.seatbid[0].bid[0].ext.dsa
        verifyAll {
            actualDsa.transparency[0].domain == bidDsa.transparency[0].domain
            actualDsa.transparency[0].params == bidDsa.transparency[0].params
            actualDsa.adrender == bidDsa.adrender
            actualDsa.behalf == bidDsa.behalf
            actualDsa.paid == bidDsa.paid
        }

        where:
        dsaRequired << [0, 1, 2, 3]
    }

    def "PBS should send DSA to bidder and fail on response when DSA is required and bidder does not return DSA"() {
        given: "Default bid request with DSA"
        def dsa = RegsDsa.getDefaultRegsDsa(dsaRequired)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = bidExt
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        verifyAll {
            bidderRequests.regs.ext.dsa.required == dsa.required
            bidderRequests.regs.ext.dsa.datatopub == dsa.datatopub
            bidderRequests.regs.ext.dsa.pubrender == dsa.pubrender
            bidderRequests.regs.ext.dsa.transparency[0].domain == dsa.transparency[0].domain
            bidderRequests.regs.ext.dsa.transparency[0].params == dsa.transparency[0].params
        }

        and: "Response should contain error"
        def expectedBidId = bidResponse.seatbid[0].bid[0].id
        verifyAll {
            response.seatbid.isEmpty()
            response.ext?.errors[GENERIC]*.code == [5]
            response.ext?.errors[GENERIC]*.message == ["BidId `$expectedBidId` validation messages: Error: Bid \"$expectedBidId\" missing DSA"]
        }

        where:
        dsaRequired || bidExt
        2           || new BidExt("dsa": null)
        3           || null
    }
}
