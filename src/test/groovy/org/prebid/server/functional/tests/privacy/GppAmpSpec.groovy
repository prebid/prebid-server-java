package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UsV1Consent

import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GppAmpSpec extends PrivacyBaseSpec {

    def "PBS should populate bid request with regs when consent type is GPP and consent string, gppSid are present"() {
        given: "Default AmpRequest with consent_type = gpp"
        def gppSids = "${TCF_EU_V2.value},${USP_V1.value}" as String
        def consentString = new TcfEuV2Consent.Builder().build().toString()
        def ampRequest = getGppAmpRequest(consentString, gppSids)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain consent string from amp request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.regs.gpp == consentString
        assert bidderRequests.regs.gppSid == [TCF_EU_V2.intValue, USP_V1.intValue]

        and: "Response shouldn't contain any warnings"
        assert !ampResponse.ext?.warnings
    }

    def "PBS should populate bid request with regs.gppSid when consent type isn't GPP and gppSid is present"() {
        given: "Default AmpRequest with consent_type = gpp"
        def consentString = PBSUtils.randomString
        def gppSids = "${TCF_EU_V2.value},${USP_V1.value}" as String
        def ampRequest = getGppAmpRequest(consentString, gppSids, ConsentType.TCF_2)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain regs.gpp"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests.regs.gpp
        assert bidderRequests.regs.gppSid == [TCF_EU_V2.intValue, USP_V1.intValue]
    }

    def "PBS shouldn't populate bid request with regs when consent type is GPP and gppSid contain invalid value"() {
        given: "Default AmpRequest with consent_type = gpp"
        def consentString = PBSUtils.randomString
        def gppSids = "${TCF_EU_V2.value},${PBSUtils.randomString}" as String
        def ampRequest = getGppAmpRequest(consentString, gppSids)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain regs.gpp"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests.regs.gpp
        assert !bidderRequests.regs.gppSid

        and: "Repose should contain warning"
        assert ampResponse.ext?.warnings[PREBID]*.code == [999]
        assert ampResponse.ext?.warnings[PREBID]*.message[0].startsWith("Failed to parse gppSid: \'${gppSids}\'")
    }

    def "PBS should emit warning when consent_string is invalid"() {
        given: "Default amp request with invalid consent_string"
        def invalidConsentString = "Invalid_Consent_String"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.consentString = invalidConsentString
            consentType = GPP
        }

        and: "Save storedRequest into DB"
        def bidRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, bidRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message.every { it.contains("GPP string invalid:") }

        and: "Bidder request should contain gpp from consent string"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.regs.gpp == invalidConsentString
    }

    def "PBS should copy consent_string to regs.gpp when consent_string is valid"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def ampRequest = getGppAmpRequest(gppConsent.toString())

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain regs.gpp from consent_string"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.regs.gpp == gppConsent as String

        where:
        gppConsent << [new TcfEuV2Consent.Builder().build(),
                       new UsV1Consent.Builder().build()]
    }

    def "PBS should copy consent_string to user.consent and set gdpr=1 when consent_string is valid and gppSid contains 2"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new TcfEuV2Consent.Builder().build()
        def gppSidIds = TCF_EU_V2.value
        def ampRequest = getGppAmpRequest(gppConsent.toString(), gppSidIds)

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs = new Regs(gdpr: null)
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "PBS shouldn't be perform bidder call"
        def bidderRequest = bidder.getBidderRequests(ampStoredRequest.id)
        assert bidderRequest.size() == 0

        and: "Resolved request should contain user.consent, gdpr=1 and gpp form amp stored request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest
        assert resolvedRequest.regs.gdpr == 1
        assert resolvedRequest.user.consent == gppConsent.encodeSection()
        assert resolvedRequest.regs.gppSid == [TCF_EU_V2.intValue]
    }

    def "PBS should copy consent_string to user.us_privacy when consent_string contains us_privacy and gppSid contains 6"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new UsV1Consent.Builder().build()
        def gppSidIds = USP_V1.value
        def ampRequest = getGppAmpRequest(gppConsent.consentString, gppSidIds)

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain regs.usPrivacy from consent_string and regs.gppSid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.regs.usPrivacy == gppConsent.encodeSection()
        assert bidderRequest.regs.gppSid == [USP_V1.intValue]
    }

    def "PBS should populate regs.ext.gpc from header when sec-gpc header has value 1"() {
        given: "Default amp request"
        def ampRequest = getGppAmpRequest(new TcfEuV2Consent.Builder().build().toString())

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs.ext = new RegsExt(gpc: null)
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        defaultPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Bidder request should contain gpc value from header"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.regs.ext.gpc == gpcHeader as String

        where:
        gpcHeader << [VALID_VALUE_FOR_GPC_HEADER as Integer, VALID_VALUE_FOR_GPC_HEADER]
    }

    def "PBS shouldn't populate regs.ext.gpc from header when sec-gpc header hasn't value 1"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new TcfEuV2Consent.Builder().build()
        def gppSidIds = TCF_EU_V2.value
        def ampRequest = getGppAmpRequest(gppConsent.toString(), gppSidIds)

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs.ext = new RegsExt(gpc: null)
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        defaultPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": PBSUtils.randomNumber as String])

        then: "Bidder request shouldn't contain gpc value from header"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequest?.regs?.ext?.gpc
    }
}
