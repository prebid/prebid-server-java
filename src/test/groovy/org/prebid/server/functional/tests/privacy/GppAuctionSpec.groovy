package org.prebid.server.functional.tests.privacy

import com.iab.gpp.encoder.section.TcfCaV1
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspV1
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.GppConsent

import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.US_PV_V1
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

class GppAuctionSpec extends BaseSpec {

    def "PBS should populate gdpr to 1 when regs.gdpr is not specified and gppSid contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = null
            regs.gppSid = [TCF_EU_V2.valueAsInt]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.gdpr"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gdpr == 1
    }

    def "PBS should populate gdpr to 0 when regs.gdpr is not specified and gppSid not contains 2"() {
        given: "Default bid request with gppSid and without gdpr"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = null
            regs.gppSid = [PBSUtils.getRandomNumber(TCF_EU_V2.valueAsInt)]
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.gdpr"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gdpr == 0
    }

    def "PBS should emit warnings when regs.gpp_sid contains 2 and regs.gdpr isn't 1"() {
        given: "Default bid request with gppSid and gdpr"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = 0
            regs.gppSid = [TCF_EU_V2.valueAsInt]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP scope does not match TCF2 scope"]
    }

    def "PBS should emit warnings when regs.gpp_sid not contains 2 and regs.gdpr isn't 0"() {
        given: "Default bid request with gppSid and gdpr"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gdpr = 1
            regs.gppSid = [PBSUtils.getRandomNumberWithExcept(TCF_EU_V2.valueAsInt)]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP scope does not match TCF2 scope"]
    }

    def "PBS should emit warning when GPP string is invalid"() {
        given: "Default bid request with invalid gpp"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gpp = "Invalid_GPP_Consent_String"
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP string invalid: Undecodable FibonacciIntegerRange '101111'"]
    }

    def "PBS should copy regs.gpp to user.consent when gppSid contains 2, gpp is TCF2-EU and user.consent isn't specified"() {
        given: "Default bid request with gpp and gppSid"
        def gppConsent = new GppConsent().setFieldValue(TcfEuV2.NAME)
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
        assert bidderRequest.user.consent == gppConsent as String
        assert bidderRequest.regs.gpp == gppConsent as String
    }

    def "PBS should emit warning when gppSid contains 2, gpp is TCF2-EU and regs.gpp and user.consent are different"() {
        given: "Default bid request with gpp and gppSid"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [TCF_EU_V2.valueAsInt]
            regs.gpp = new GppConsent().setFieldValue(TcfEuV2.NAME)
            user = new User(consent: new GppConsent().setFieldValue(TcfCaV1.NAME))
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["GPP TCF2 string does not match user.consent"]
    }

    def "PBS should copy regs.gpp to regs.usPrivacy when gppSid contains 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def gppConsent = new GppConsent().setFieldValue(UspV1.NAME)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_PV_V1.valueAsInt]
            regs.gpp = gppConsent
            regs.usPrivacy = null
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.usPrivacy == gppConsent as String
        assert bidderRequest.regs.gpp == gppConsent as String
    }

    def "PBS shouldn't copy regs.gpp to regs.usPrivacy when gppSid doesn't contain 6, gpp is USP_V1 and regs.us_privacy isn't specified"() {
        given: "Default bid request with gpp and gppSid, without us_privacy"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [PBSUtils.getRandomNumberWithExcept(US_PV_V1.valueAsInt)]
            regs.gpp = new GppConsent().setFieldValue(UspV1.NAME)
            regs.usPrivacy = null
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain regs.usPrivacy from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.regs.usPrivacy
    }

    def "PBS should emit warning when gppSid contains 6, gpp is USP_V1 and regs.gpp and regs.usPrivacy are different"() {
        given: "Default bid request with gpp, gppSid and usPrivacy"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_PV_V1.valueAsInt]
            regs.gpp = new GppConsent().setFieldValue(UspV1.NAME)
            regs.usPrivacy = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["USP string does not match regs.us_privacy"]
    }
}
