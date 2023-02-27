package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UspV1Consent

import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.NOT_ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class AuctionGppSpec extends BaseSpec {

    def "PBS should populate gdpr to 1 when regs.gdpr is not specified and gppSid contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def gppSidsId = [TCF_EU_V2.valueAsInt]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gdpr: null, gppSid: gppSidsId)
        }

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.gdpr and gppSid from request"
        def resolvedRequest = bidResponse.ext.debug.resolvedRequest.regs
        assert resolvedRequest.gdpr == 1
        assert resolvedRequest.gppSid == gppSidsId
    }

    def "PBS should populate gdpr to 0 when regs.gdpr is not specified and gppSid not contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def gppSidsId = [PBSUtils.getRandomNumber(TCF_EU_V2.valueAsInt)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gdpr: null, gppSid: gppSidsId)
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.gdpr and gppSid from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gdpr == 0
        assert bidderRequest.regs.gppSid == gppSidsId
    }

    def "PBS should emit warnings when regs.gpp_sid contains 2 and regs.gdpr isn't 1"() {
        given: "Default bid request with gppSid and gdpr"
        def gdpr = 0
        def gppSectionsId = [TCF_EU_V2.valueAsInt]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = gdpr
            regs.gppSid = gppSectionsId
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP scope does not match TCF2 scope"]

        and: "Bidder request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gdpr == gdpr
        assert bidderRequest.regs.gppSid == gppSectionsId
    }

    def "PBS should emit warnings when regs.gpp_sid not contains 2 and regs.gdpr isn't 0"() {
        given: "Default bid request with gppSid and gdpr"
        def gdpr = 1
        def gppSidsId = [PBSUtils.getRandomNumberWithExcept(TCF_EU_V2.valueAsInt)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = gdpr
            regs.gppSid = gppSidsId
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP scope does not match TCF2 scope"]

        and: "Bidder request should contain the same value as in request"
        def resolvedRequest = response.ext.debug.resolvedRequest
        assert resolvedRequest.regs.gdpr == gdpr
        assert resolvedRequest.regs.gppSid == gppSidsId
    }

    def "PBS should emit warning when GPP string is invalid"() {
        given: "Default bid request with invalid gpp"
        def invalidGpp = "Invalid_GPP_Consent_String"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: invalidGpp)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP string invalid: Undecodable FibonacciIntegerRange '101111'"]

        and: "Bid request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == invalidGpp
    }

    def "PBS should copy regs.gpp to user.consent when gppSid contains 2, gpp is TCF2-EU and user.consent isn't specified"() {
        given: "Default bid request with gpp and gppSid"
        def gppConsent = new TcfEuV2Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [TCF_EU_V2.valueAsInt]
            regs.gpp = gppConsent
            user = new User().tap {
                consent = null
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain user.consent from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gppConsent.consentString
        assert bidderRequest.user.consent == gppConsent.toString().substring(7)
    }

    def "PBS should emit warning when gppSid contains 2, gpp is TCF2-EU and regs.gpp and user.consent are different"() {
        given: "Default bid request with gpp and gppSid"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def gppSidsId = [TCF_EU_V2.valueAsInt]
        def gpp = new TcfEuV2Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: gpp, gppSid: gppSidsId)
            user = new User(consent: validConsentString)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP TCF2 string does not match user.consent"]

        and: "Bidder request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gpp as String
        assert bidderRequest.regs.gppSid == gppSidsId
        assert bidderRequest.user.consent == validConsentString as String
    }

    def "PBS should copy regs.gpp to regs.usPrivacy when gppSid contains 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def gppConsent = new UspV1Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: gppConsent, gppSid: [USP_V1.valueAsInt], usPrivacy: null)
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.usPrivacy == gppConsent.toString().substring(7)
        assert bidderRequest.regs.gpp == gppConsent as String
    }

    def "PBS shouldn't copy regs.gpp to regs.usPrivacy when gppSid doesn't contain 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs().tap {
                gppSid = [PBSUtils.getRandomNumberWithExcept(USP_V1.valueAsInt)]
                gpp = new UspV1Consent.Builder().build()
                usPrivacy = null
            }
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.regs.usPrivacy
    }

    def "PBS should emit warning when gppSid contains 6, gpp is USP_V1 and regs.gpp and regs.usPrivacy are different"() {
        given: "Default bid request with gpp, gppSid and usPrivacy"
        def gppSidsId = [USP_V1.valueAsInt]
        def gpp = new UspV1Consent.Builder().build()
        def ccpaConsent = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: NOT_ENFORCED)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gppSid: gppSidsId, gpp: gpp, usPrivacy: ccpaConsent)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["USP string does not match regs.us_privacy"]

        and: "Bidder request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gpp as String
        assert bidderRequest.regs.gppSid == gppSidsId
        assert bidderRequest.regs.usPrivacy == ccpaConsent as String
    }
}
