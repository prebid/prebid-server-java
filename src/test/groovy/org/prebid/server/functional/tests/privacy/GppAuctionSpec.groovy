package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.response.auction.ErrorType
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

class GppAuctionSpec extends PrivacyBaseSpec {

    def "PBS should populate gdpr to 1 when regs.gdpr is not specified and gppSid contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def gppSidIds = [TCF_EU_V2.intValue]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gdpr: null, gppSid: gppSidIds)
        }

        when: "PBS processes auction request"
        def bidResponse = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Resolved request regs should contain regs.gdpr and gppSid from request"
        def regs = bidResponse.ext.debug.resolvedRequest.regs
        assert regs.gdpr == 1
        assert regs.gppSid == gppSidIds

        and: "Bidder wasn't be call due to lack the consent string"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 0
    }

    def "PBS should populate gdpr to 0 when regs.gdpr is not specified and gppSid not contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def gppSidIds = [PBSUtils.getRandomNumberWithExclusion(TCF_EU_V2.intValue)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gdpr: null, gppSid: gppSidIds)
        }

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.gdpr and gppSid from request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gdpr == 0
        assert bidderRequest.regs.gppSid == gppSidIds
    }

    def "PBS should emit warnings when regs.gpp_sid contains 2 and regs.gdpr isn't 1"() {
        given: "Default bid request with gppSid and gdpr"
        def gdpr = 0
        def gppSectionsId = [TCF_EU_V2.intValue]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = gdpr
            regs.gppSid = gppSectionsId
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

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
        def gppSidIds = [PBSUtils.getRandomNumberWithExclusion(TCF_EU_V2.intValue)]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = gdpr
            regs.gppSid = gppSidIds
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP scope does not match TCF2 scope"]

        and: "Resolved request should contain the same value as in request"
        def resolvedRequest = response.ext.debug.resolvedRequest
        assert resolvedRequest.regs.gdpr == gdpr
        assert resolvedRequest.regs.gppSid == gppSidIds

        and: "Bidder wasn't be call due to lack the consent string"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 0
    }

    def "PBS should emit warning when GPP string is invalid"() {
        given: "Default bid request with invalid gpp"
        def invalidGpp = "Invalid_GPP_Consent_String"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: invalidGpp)
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        response.ext?.warnings[ErrorType.PREBID]?.collect { it.message }
                .any { it.contains("GPP string invalid:") }

        and: "Bid request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == invalidGpp
    }

    def "PBS should copy regs.gpp to user.consent when gppSid contains 2, gpp is TCF2-EU and user.consent isn't specified"() {
        given: "Default bid request with gpp and gppSid"
        def gppConsent = new TcfEuV2Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [TCF_EU_V2.intValue]
            regs.gpp = gppConsent
            user = new User(consent: null)
        }

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain user.consent from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gppConsent.consentString
        assert bidderRequest.user.consent == gppConsent.encodeSection()
    }

    def "PBS should emit warning when gppSid contains 2, gpp is TCF2-EU and regs.gpp and user.consent are different"() {
        given: "Default bid request with gpp and gppSid"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def gppSidIds = [TCF_EU_V2.intValue]
        def gpp = new TcfEuV2Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: gpp, gppSid: gppSidIds)
            user = new User(consent: validConsentString)
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP TCF2 string does not match user.consent"]

        and: "Bidder request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gpp as String
        assert bidderRequest.regs.gppSid == gppSidIds
        assert bidderRequest.user.consent == validConsentString as String
    }

    def "PBS should copy regs.gpp to regs.usPrivacy when gppSid contains 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def gppConsent = new UspV1Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: gppConsent, gppSid: [USP_V1.intValue], usPrivacy: null)
        }

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.usPrivacy == gppConsent.encodeSection()
        assert bidderRequest.regs.gpp == gppConsent as String
    }

    def "PBS shouldn't copy regs.gpp to regs.usPrivacy when gppSid doesn't contain 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def gppSidIds = [PBSUtils.getRandomNumberWithExclusion(USP_V1.intValue)]
        def gpp = new UspV1Consent.Builder().build()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gppSid: gppSidIds, gpp: gpp, usPrivacy: null)
        }

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain user and regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.regs.usPrivacy
        assert !bidderRequest.user

        and: "Should contain in bidder request regs from bid request regs"
        assert bidderRequest.regs.gppSid == gppSidIds
        assert bidderRequest.regs.gpp == gpp as String
    }

    def "PBS should emit warning when gppSid contains 6, gpp is USP_V1 and regs.gpp and regs.usPrivacy are different"() {
        given: "Default bid request with gpp, gppSid and usPrivacy"
        def gppSidIds = [USP_V1.intValue]
        def gpp = new UspV1Consent.Builder().build()
        def ccpaConsent = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: NOT_ENFORCED)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gppSid: gppSidIds, gpp: gpp, usPrivacy: ccpaConsent)
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["USP string does not match regs.us_privacy"]

        and: "Bidder request should contain the same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == gpp as String
        assert bidderRequest.regs.gppSid == gppSidIds
        assert bidderRequest.regs.usPrivacy == ccpaConsent as String
    }

    def "PBS should populate gpc when header sec-gpc has value 1"() {
        given: "Default bid request with gpc"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.gpc = null
        }

        when: "PBS processes auction request with headers"
        privacyPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": gpcHeadre])

        then: "Bidder request should contain gpc from header"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.regs.ext.gpc == gpcHeadre as String

        where:
        gpcHeadre << ["1", 1]
    }

    def "PBS shouldn't populate gpc when header sec-gpc has #gpcInvalid value"() {
        given: "Default bid request with gpc"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.gpc = null
        }

        when: "PBS processes auction request with headers"
        privacyPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": gpcInvalid])

        then: "Bidder request shouldn't contain gpc from header"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequests.regs.ext

        where:
        gpcInvalid << [PBSUtils.randomNumber as String, PBSUtils.randomNumber, PBSUtils.randomString, Boolean.TRUE]

    }

    def "PBS should take precedence from request gpc when header sec-gpc has 1 value"() {
        given: "Default bid request with gpc"
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext.gpc = randomGpc
        }

        when: "PBS processes auction request with headers"
        privacyPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": "1"])

        then: "Bidder request should contain gpc from header"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.regs.ext.gpc == randomGpc
    }
}
