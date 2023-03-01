package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.GppSectionId.US_PV_V1
import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2

class GppAmpSpec extends PrivacyBaseSpec {

    def "PBS should populate bid request with regs when consent type is GPP and consent string, gppSid are present"() {
        given: "Default AmpRequest with consent_type = gpp"
        def consentString = PBSUtils.randomString
        def gppSids = "${TCF_EU_V2.value},${US_PV_V1.value}" as String
        def ampRequest = getGppAmpRequest(consentString, gppSids)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain consent string from amp request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.regs.gpp == consentString
        assert bidderRequests.regs.gppSid == [TCF_EU_V2.value.toInteger(), US_PV_V1.value.toInteger()]
    }

    def "PBS should populate bid request with regs.gppSid when consent type isn't GPP and gppSid is present"() {
        given: "Default AmpRequest with consent_type = gpp"
        def consentString = PBSUtils.randomString
        def gppSids = "${TCF_EU_V2.value},${US_PV_V1.value}" as String
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
        assert bidderRequests.regs.gppSid == [TCF_EU_V2.value.toInteger(), US_PV_V1.value.toInteger()]
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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't contain regs.gpp"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests.regs.gpp
        assert !bidderRequests.regs.gppSid
    }
}
