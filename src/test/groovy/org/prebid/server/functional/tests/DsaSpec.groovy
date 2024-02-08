package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Dsa as RequestDsa
import org.prebid.server.functional.model.request.auction.DsaRequired
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.Dsa as BidDsa

import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class DsaSpec extends BaseSpec {

    def "AMP request should always forward DSA to bidders"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain DSA"
        assert bidder.getBidderRequest(ampStoredRequest.id).regs?.ext?.dsa == dsa

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(DsaRequired.NOT_REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.SUPPORTED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
    }

    def "AMP request should always accept bids with DSA"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response with DSA"
        def bidDsa = BidDsa.getDefaultDsa()
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: bidDsa)
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS should return bid"
        assert response.targeting

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(DsaRequired.NOT_REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.SUPPORTED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
    }

    def "AMP request should accept bids without DSA when dsarequired is #dsaRequired"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def dsa = RequestDsa.getDefaultDsa(dsaRequired)

        and: "Default stored request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: null)
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS should return bid"
        assert response.targeting

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsaRequired << [DsaRequired.NOT_REQUIRED,
                        DsaRequired.SUPPORTED]
    }

    def "AMP request should reject bids without DSA when dsarequired is #dsaRequired"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def dsa = RequestDsa.getDefaultDsa(dsaRequired)

        and: "Default stored bid request with DSA"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response without DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: null)
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS should reject bid"
        assert !response.targeting

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `$bidId` validation messages: Error: Bid \"$bidId\" missing DSA"]

        where:
        dsaRequired << [DsaRequired.REQUIRED,
                        DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM]
    }

    def "Auction request should always forward DSA to bidders"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA"
        assert bidder.getBidderRequest(bidRequest.id).regs?.ext?.dsa == dsa

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(DsaRequired.NOT_REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.SUPPORTED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
    }

    def "Auction request should always accept bids with DSA"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        and: "Default bidder response with DSA"
        def bidDsa = BidDsa.getDefaultDsa()
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: bidDsa)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return bid"
        assert response.seatbid[0].bid[0]

        and: "Returned bid should contain DSA"
        assert response.seatbid[0].bid[0].ext.dsa

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(DsaRequired.NOT_REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.SUPPORTED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED),
                RequestDsa.getDefaultDsa(DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
    }

    def "Auction request should accept bids without DSA when dsarequired is #dsaRequired"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = RequestDsa.getDefaultDsa(dsaRequired)
        }

        and: "Default bidder response with DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: null)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return bid"
        assert response.seatbid[0].bid[0]

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsaRequired << [DsaRequired.NOT_REQUIRED,
                        DsaRequired.SUPPORTED]
    }

    def "Auction request should reject bids without DSA when dsarequired is #dsaRequired"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = RequestDsa.getDefaultDsa(dsaRequired)
        }

        and: "Default bidder response without DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: null)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject bid"
        assert !response.seatbid

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `$bidId` validation messages: Error: Bid \"$bidId\" missing DSA"]

        where:
        dsaRequired << [DsaRequired.REQUIRED,
                        DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM]
    }
}
