package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.TcfEuV2Consent
import org.prebid.server.functional.util.privacy.gpp.UspV1Consent

import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GppAmpSpec extends PrivacyBaseSpec {

    def "PBS should emit warning when consent_string is invalid"() {
        given: "Default amp request with invalid consent_string"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            consentString = PBSUtils.randomString
            consentType = GPP
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, BidRequest.defaultStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message.every { it.contains("GPP string invalid:") }
    }

    def "PBS should copy consent_string to regs.gpp when consent_string is valid"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def ampRequest = getGppAmpRequest(gppConsent)

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
                       new UspV1Consent.Builder().build()]
    }

    def "PBS should copy consent_string to user.consent and set gdpr=1 when consent_string is valid and gppSid contains 2"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new TcfEuV2Consent.Builder().build()
        def ampRequest = getGppAmpRequest(gppConsent)

        and: "Save storedRequest into DB"
        def gppSidIds = [TCF_EU_V2.valueAsInt]
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs = new Regs(gppSid: gppSidIds, gdpr: null)
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
        assert resolvedRequest.user.consent == gppConsent.encodeSection()
        assert resolvedRequest.regs.gdpr == 1
        assert resolvedRequest.regs.gppSid == gppSidIds
    }

    def "PBS should copy consent_string to user.us_privacy when consent_string contains us_privacy string"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new UspV1Consent.Builder().build()
        def ampRequest = getGppAmpRequest(gppConsent)

        and: "Save storedRequest into DB"
        def gppSidIds = [USP_V1.valueAsInt]
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs = new Regs(gppSid: gppSidIds)
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain regs.usPrivacy from consent_string"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.regs.usPrivacy == gppConsent.encodeSection()
    }
}
