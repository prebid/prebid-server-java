package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.v1.UspV1Consent
import org.prebid.server.functional.util.privacy.gpp.v2.TcfEuV2Consent

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.GppSectionId.US_NAT_V1
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class GppSetUidSpec extends PrivacyBaseSpec {

    def "PBS setUid shouldn't reject request when gpp is invalid"() {
        given: "Set uid request with invalid GPP"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gpp = "Invalid_GPP_Consent_String"
            it.gppSid = US_NAT_V1.value
            it.uid = UUID.randomUUID().toString()
            it.gdpr = null
            it.gdprConsent = null
        }

        when: "PBS processes send uid request"
        def response = privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Privacy for bidder should not be enforced"
        assert response.uidsCookie.tempUIDs[GENERIC]
    }

    def "PBS setUid should reject request when gppSid is invalid"() {
        given: "Set uid request with invalid GPP SID"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gpp = null
            it.gppSid = [PBSUtils.randomString]
            it.uid = UUID.randomUUID().toString()
            it.gdpr = null
            it.gdprConsent = null
        }

        when: "PBS processes send uid request"
        privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Response should respond with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody ==
                "Invalid request format: invalid gpp_sid value. Comma separated integers expected."
    }

    def "PBS setUid shouldn't reject request when gpp doesn't corresponding to gpp_sid"() {
        given: "Set uid request with invalid GPP"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gpp = gpp
            it.gppSid = "${PBSUtils.randomNumber.toString()}"
            it.uid = UUID.randomUUID().toString()
            it.gdpr = null
            it.gdprConsent = null
        }

        when: "PBS processes send uid request"
        def response = privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Privacy for bidder should not be enforced"
        assert response.uidsCookie.tempUIDs[GENERIC]

        where:
        gpp << [new UspV1Consent.Builder().build().encodeSection(),
                new TcfEuV2Consent.Builder().build().encodeSection()]
    }

    def "PBS setUid should reject request by TcfEuV2 when gpp, gppSid valid"() {
        given: "Set uid request with invalid GPP"
        def setUidRequest = SetuidRequest.defaultSetuidRequest.tap {
            def tcfEuV2Consent = new TcfEuV2Consent.Builder()
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
                    .encodeSection()
            it.gpp = tcfEuV2Consent
            it.gppSid = "$GppSectionId.TCF_EU_V2.intValue"
            it.uid = UUID.randomUUID().toString()
            it.gdpr = 1
            it.gdprConsent = tcfEuV2Consent
        }

        when: "PBS processes send uid request"
        privacyPbsService.sendSetUidRequest(setUidRequest, UidsCookie.defaultUidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "The gdpr_consent param prevents cookies from being saved"
    }
}
