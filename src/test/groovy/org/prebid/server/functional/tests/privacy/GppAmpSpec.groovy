package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.GppConsent

import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

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

        then: "Response should contain error"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == ["Invalid GPP consent_string."]
    }

    def "PBS should copy consent_string to regs.gpp when consent_string is valid"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new GppConsent().setFieldValue()
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
    }

    def "PBS should copy consent_string to user.consent and set gdrp=1 when consent_string is valid and gppSid contains 2"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def gppConsent = new GppConsent().setFieldValue()
        def ampRequest = getGppAmpRequest(gppConsent)

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            regs.gppSid = [2]
            regs.gdpr = 0
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain regs.gdpr and user.consent from regs.gpp"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.user.consent == gppConsent as String
        assert bidderRequest.regs.gdpr == 1
    }

    def "PBS should copy consent_string to user.us_privacy when consent_string contains us_privacy string"() {
        given: "Default amp request with valid consent_string and gpp consent_type"
        def ccpaConsent = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getGppAmpRequest(ccpaConsent)

        and: "Save storedRequest into DB"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain regs.usPrivacy from consent_string"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.regs.usPrivacy == ccpaConsent as String
    }
}
