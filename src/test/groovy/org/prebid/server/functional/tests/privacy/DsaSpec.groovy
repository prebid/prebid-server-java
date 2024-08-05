package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountDsaConfig
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Dsa
import org.prebid.server.functional.model.request.auction.Dsa as RequestDsa
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.DsaResponse
import org.prebid.server.functional.model.response.auction.DsaResponse as BidDsa
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent

import static org.prebid.server.functional.model.request.auction.DsaPubRender.PUB_CANT_RENDER
import static org.prebid.server.functional.model.request.auction.DsaPubRender.PUB_WILL_RENDER
import static org.prebid.server.functional.model.request.auction.DsaRequired.NOT_REQUIRED
import static org.prebid.server.functional.model.request.auction.DsaRequired.REQUIRED
import static org.prebid.server.functional.model.request.auction.DsaRequired.REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM
import static org.prebid.server.functional.model.request.auction.DsaRequired.SUPPORTED
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REJECTED_DUE_TO_DSA
import static org.prebid.server.functional.model.response.auction.DsaAdRender.ADVERTISER_WILL_RENDER
import static org.prebid.server.functional.model.response.auction.DsaAdRender.ADVERTISER_WONT_RENDER
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class DsaSpec extends PrivacyBaseSpec {

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
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain DSA"
        assert bidder.getBidderRequest(ampStoredRequest.id).regs?.ext?.dsa == dsa

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(NOT_REQUIRED),
                RequestDsa.getDefaultDsa(SUPPORTED),
                RequestDsa.getDefaultDsa(REQUIRED),
                RequestDsa.getDefaultDsa(REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
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
        def bidDsa = BidDsa.defaultDsa
        def bidResponse = BidResponse.getDefaultBidResponse(ampStoredRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: bidDsa)
        }

        and: "Set bidder response"
        bidder.setResponse(ampStoredRequest.id, bidResponse)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS should return bid"
        assert response.targeting

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(NOT_REQUIRED),
                RequestDsa.getDefaultDsa(SUPPORTED),
                RequestDsa.getDefaultDsa(REQUIRED),
                RequestDsa.getDefaultDsa(REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
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
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS should return bid"
        assert response.targeting

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsaRequired << [NOT_REQUIRED, SUPPORTED]
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
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS should reject bid"
        assert !response.targeting

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["Bid \"$bidId\": DSA object missing when required"]

        where:
        dsaRequired << [REQUIRED, REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM]
    }

    def "Auction request should always forward DSA to bidders"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA"
        assert bidder.getBidderRequest(bidRequest.id).regs?.ext?.dsa == dsa

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(NOT_REQUIRED),
                RequestDsa.getDefaultDsa(SUPPORTED),
                RequestDsa.getDefaultDsa(REQUIRED),
                RequestDsa.getDefaultDsa(REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
    }

    def "Auction request should always accept bids with DSA"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.dsa = dsa
        }

        and: "Default bidder response with DSA"
        def bidDsa = BidDsa.defaultDsa
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: bidDsa)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return bid"
        assert response.seatbid.bid

        and: "Returned bid should contain DSA"
        assert response.seatbid[0].bid[0].ext.dsa == bidDsa

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsa << [null,
                new RequestDsa(),
                RequestDsa.getDefaultDsa(NOT_REQUIRED),
                RequestDsa.getDefaultDsa(SUPPORTED),
                RequestDsa.getDefaultDsa(REQUIRED),
                RequestDsa.getDefaultDsa(REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM)]
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
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return bid"
        assert response.seatbid.bid

        and: "PBS should not log warning"
        assert !response.ext.warnings
        assert !response.ext.errors

        where:
        dsaRequired << [NOT_REQUIRED, SUPPORTED]
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
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject bid"
        assert !response.seatbid

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["Bid \"$bidId\": DSA object missing when required"]

        where:
        dsaRequired << [REQUIRED, REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM]
    }

    def "Auction request should reject bids without DSA and populate seatNonBid when dsarequired is #dsaRequired"() {
        given: "Default bid request with DSA"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            regs.ext.dsa = RequestDsa.getDefaultDsa(dsaRequired)
        }

        and: "Default bidder response without DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: null)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject bid"
        assert !response.seatbid

        and: "PBS response should contain seatNonBid for rejected bids"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REJECTED_DUE_TO_DSA

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["Bid \"$bidId\": DSA object missing when required"]

        where:
        dsaRequired << [REQUIRED, REQUIRED_PUBLISHER_IS_ONLINE_PLATFORM]
    }

    def "Auction request should set account DSA when BidRequest DSA is null"() {
        given: "Default bid request without DSA"
        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.ext.dsa = null
        }

        and: "Account with default DSA config"
        def accountDsa = Dsa.defaultDsa
        def account = getAccountWithDsa(accountId, new AccountDsaConfig(defaultDsa: accountDsa))
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA from account config"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.dsa == accountDsa
    }

    def "Auction request shouldn't set account DSA when BidRequest DSA is not null"() {
        given: "Default bid request with DSA"
        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.ext.dsa = requestDsa
        }

        and: "Account with default DSA config"
        def account = getAccountWithDsa(accountId, new AccountDsaConfig(defaultDsa: accountDsa))
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA from request"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.dsa == requestDsa

        where:
        requestDsa     || accountDsa
        new Dsa()      || null
        new Dsa()      || Dsa.defaultDsa
        Dsa.defaultDsa || null
        Dsa.defaultDsa || Dsa.defaultDsa
    }

    def "Auction request shouldn't populate DSA when account DSA is null and request DSA is null"() {
        given: "Default bid request without DSA"
        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.ext.dsa = null
        }

        and: "Account without default DSA config"
        def account = getAccountWithDsa(accountId, new AccountDsaConfig(defaultDsa: null))
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain DSA"
        assert !bidder.getBidderRequest(bidRequest.id)?.regs?.ext?.dsa
    }

    def "Auction request should set account DSA when gdpr-only is false and not in GDPR scope"() {
        given: "Default bid request not in GDPR scope without DSA"
        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.ext.dsa = null
            regs.ext.gdpr = 0
        }

        and: "Account with default DSA config"
        def accountDsa = Dsa.defaultDsa
        def account = getAccountWithDsa(accountId,
                new AccountDsaConfig(defaultDsa: accountDsa, gdprOnly: false))
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA from account config"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.dsa == accountDsa
    }

    def "Auction request should set account DSA when gdpr-only is #gdprOnly and in GDPR scope"() {
        given: "Default bid request in GDPR scope with DSA"
        def consentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = getGdprBidRequest(consentString).tap {
            setAccountId(accountId)
            regs.ext.dsa = null
        }

        and: "Account with default DSA config"
        def accountDsa = Dsa.defaultDsa
        def account = getAccountWithDsa(accountId,
                new AccountDsaConfig(defaultDsa: accountDsa, gdprOnly: gdprOnly))
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain DSA from account config"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.dsa == accountDsa

        where:
        gdprOnly << [true, false]
    }

    def "Auction request shouldn't set account DSA when gdpr-only is true and not in GDPR scope"() {
        given: "Default bid request not in GDPR scope without DSA"
        def accountId = PBSUtils.randomNumber.toString()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            regs.ext.dsa = null
            regs.ext.gdpr = 0
        }

        and: "Account with default DSA config"
        def account = getAccountWithDsa(accountId, accountDsaConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain DSA"
        assert !bidder.getBidderRequest(bidRequest.id)?.regs?.ext?.dsa

        where:
        accountDsaConfig << [new AccountDsaConfig(defaultDsa: Dsa.defaultDsa, gdprOnly: true),
                             new AccountDsaConfig(defaultDsa: Dsa.defaultDsa, gdprOnlySnakeCase: true)]
    }

    def "Auction request should reject bids with DSA when pubRender is #pubRender and adRender is #adRender"() {
        given: "Default bid request with DSA pubRender"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            regs.ext.dsa = RequestDsa.getDefaultDsa(REQUIRED).tap {
                it.pubRender = pubRender
            }
        }

        and: "Default bidder response with incorrect DSA adRender"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: new DsaResponse(adRender: adRender))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject bid"
        assert !response.seatbid

        and: "PBS response should contain seatNonBid for rejected bids"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REJECTED_DUE_TO_DSA

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["Bid \"$bidId\": ${warningMessage}"]

        where:
        warningMessage                                        | pubRender       | adRender
        "DSA publisher and buyer both signal will render"     | PUB_WILL_RENDER | ADVERTISER_WILL_RENDER
        "DSA publisher and buyer both signal will not render" | PUB_CANT_RENDER | ADVERTISER_WONT_RENDER
    }

    def "Auction request should reject bids with DSA when dsa response have paid or behalf fields longer then 100 characters"() {
        given: "Default bid request with DSA pubRender"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            regs.ext.dsa = RequestDsa.getDefaultDsa(REQUIRED)
        }

        and: "Default bidder response with incorrect DSA"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(dsa: invalidDsaResponse)
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject bid"
        assert !response.seatbid

        and: "PBS response should contain seatNonBid for rejected bids"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REJECTED_DUE_TO_DSA

        and: "Response should contain an error"
        def bidId = bidResponse.seatbid[0].bid[0].id
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["Bid \"$bidId\": ${warningMessage}"]

        where:
        warningMessage                          | invalidDsaResponse
        "DSA paid exceeds limit of 100 chars"   | new DsaResponse(paid: PBSUtils.getRandomString(101))
        "DSA behalf exceeds limit of 100 chars" | new DsaResponse(behalf: PBSUtils.getRandomString(101))
    }
}
