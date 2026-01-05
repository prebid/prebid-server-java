package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ConsentedProvidersSettings
import org.prebid.server.functional.model.request.auction.DebugCondition
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.get.GeneralGetRequest
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.UsNatV1Consent

import static org.prebid.server.functional.model.config.Purpose.P2
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.GppSectionId.HEADER_V1
import static org.prebid.server.functional.model.request.GppSectionId.TCF_EU_V2
import static org.prebid.server.functional.model.request.amp.ConsentType.BOGUS
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_1
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_2
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class GeneralGetInterfacePrivacySpec extends PrivacyBaseSpec {

    def "PBS should apply gpp consent from general get request when it's specified"() {
        given: "Default General get request"
        def consentValue = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build().toString()
        def generalGetRequest = (consentGeneralRequest(consentValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain user consent info from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.user.consent == consentValue

        where:
        consentGeneralRequest <<
                [
                        { String tcfConsent -> new GeneralGetRequest(tcfConsent: tcfConsent) },
                        { String tcfConsent -> new GeneralGetRequest(tcfConsent: tcfConsent, generalConsent: PBSUtils.randomString) },
                        { String tcfConsent -> new GeneralGetRequest(tcfConsent: tcfConsent, generalConsentString: PBSUtils.randomString) },
                ]
    }

    def "PBS should recognise consent from general get request as tcfv1 when consent type is tcf1"() {
        given: "Default General get request"
        def consentValue = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build().toString()
        def generalGetRequest = (consentGeneralRequest(consentValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.consentType = TCF_1
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain user consent info from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.user.consent == consentValue

        where:
        consentGeneralRequest <<
                [
                        { String gppConsent -> new GeneralGetRequest(generalConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent, generalConsent: PBSUtils.randomString) }
                ]
    }

    def "PBS should recognise consent from general get request as us_privacy when consent type is us_privacy"() {
        given: "Default General get request"
        def generalGetRequest = (consentGeneralRequest(new CcpaConsent().getConsentString()) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.consentType = US_PRIVACY
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Generic bidderRequest should contain us privacy info"
        def bidderRequest = bidder.getBidderRequest(request.id)
        verifyAll (bidderRequest) {
            regs.usPrivacy == new CcpaConsent().getConsentString()
            regs.ext == new RegsExt()
        }

        and: "Shouldn't contain other privacy info"
        verifyAll (bidderRequest.regs) {
            !it.coppa
            !it.gpc
            !it.gpp
            !it.gppSid
        }

        where:
        consentGeneralRequest <<
                [
                        { String gppConsent -> new GeneralGetRequest(generalConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent, generalConsent: PBSUtils.randomString) }
                ]
    }

    def "PBS should recognise consent from general get request as gpp when consent type is gpp"() {
        given: "Default General get request"
        def consentValue = new UsNatV1Consent.Builder().build().toString()
        def generalGetRequest = (consentGeneralRequest(consentValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.consentType = GPP
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Generic bidderRequest should contain gpp info"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.gpp == consentValue


        and: "Shouldn't contain other privacy info"
        verifyAll (bidderRequest.regs) {
            !it.coppa
            !it.gpc
            !it.gppSid
        }

        and: "Bidder request shouldn't contain regs.ext"
        assert bidderRequest.regs.ext == new RegsExt()

        where:
        consentGeneralRequest <<
                [
                        { String gppConsent -> new GeneralGetRequest(generalConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent, generalConsent: PBSUtils.randomString) }
                ]
    }

    def "PBS get interface should emit error when consent type is invalid"() {
        given: "Default General get request"
        def consentValue = PBSUtils.randomString
        def accountId = PBSUtils.randomNumber.toString()
        def generalGetRequest = (consentGeneralRequest(consentValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.accountId = accountId
            it.consentType = BOGUS
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message == ["Invalid consent_type param passed"]

        where:
        consentGeneralRequest <<
                [
                        { String gppConsent -> new GeneralGetRequest(generalConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent, generalConsent: PBSUtils.randomString) }
                ]
    }

    def "PBS should apply gdpr from general get request when it's specified"() {
        given: "Default General get request"
        def gdprValue = PBSUtils.randomBinary
        def accountId = PBSUtils.randomNumber
        def generalGetRequest = (consentGeneralRequest(gdprValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.accountId = accountId
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId as String)
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(accountId as String, accountGdprConfig)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain user consent info from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.gdpr == gdprValue

        where:
        consentGeneralRequest << [
                { Integer gdpr -> new GeneralGetRequest(gdpr: gdpr) },
                { Integer gdpr -> new GeneralGetRequest(gdprApplies: gdpr) },
                { Integer gdpr -> new GeneralGetRequest(gdpr: gdpr, gdprApplies: PBSUtils.randomBoolean) },
        ]
    }

    def "PBS should apply usp from general get request when it's specified"() {
        given: "Default General get request"
        def usp = new CcpaConsent().getConsentString()
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.usPrivacy = usp
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain usPrivacy from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.usPrivacy == usp
    }

    def "PBS should apply addtl_consent from general get request when it's specified"() {
        given: "Default General get request"
        def addtlConsent = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.additionalConsent = addtlConsent
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain addtlConsent from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.user.ext.consentedProvidersSettings.consentedProviders == addtlConsent
    }

    def "PBS should apply gpp from general get request when it's specified"() {
        given: "Default General get request"
        def gppConsent = SIMPLE_GPC_DISALLOW_LOGIC.toString()
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.gpp = gppConsent
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain regs.gpp from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.gpp == gppConsent
    }

    def "PBS should apply gpp section ids from general get request when it's specified"() {
        given: "Default General get request"
        def gppSids = [PBSUtils.getRandomEnum(GppSectionId.class, [TCF_EU_V2, HEADER_V1])].intValue
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.gppSid = gppSids
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain gpp_sid from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.gppSid == gppSids
    }

    def "PBS should apply coppa from general get request when it's specified"() {
        given: "Default General get request"
        def coppa = PBSUtils.randomBinary
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.coppa = coppa
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain coppa from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.coppa == coppa
    }

    def "PBS should apply gpc from general get request when it's specified"() {
        given: "Default General get request"
        def gpc = PBSUtils.randomBinary
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.globalPrivacyControl = gpc
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain gpc from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.regs.ext.gpc as Integer == gpc
    }

    def "PBS should use original values from stored request when it's not specified in get request"() {
        given: "Default General get request"
        def storedRequestId = PBSUtils.randomString
        def generalGetRequest = new GeneralGetRequest(storedRequestId: storedRequestId)

        and: "Default stored request"
        def userForRequest = User.defaultUser.tap {
            it.consent = new TcfConsent.Builder().build().toString()
            it.ext = new UserExt(consentedProvidersSettings: new ConsentedProvidersSettings(consentedProviders: PBSUtils.randomString))
        }
        def regsForRequest = new Regs().tap {
            it.gdpr = 0 // for preventing bidder block
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
            it.usPrivacy = new CcpaConsent().getConsentString()
            it.gppSid = [PBSUtils.randomNumber]
            it.ext = new RegsExt(gpc: PBSUtils.randomNumber)
            it.coppa = 0
        }
        def request = BidRequest.getDefaultBidRequest().tap {
            user = userForRequest
            regs = regsForRequest
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain privacy data from original request"
        verifyAll (bidder.getBidderRequest(request.id)) {
            it.user.consent == userForRequest.consent
            it.user.ext.consentedProvidersSettings == userForRequest.ext.consentedProvidersSettings
            it.regs == regsForRequest
        }
    }

    def "PBS should properly process privacy functionality when it's required"() {
        given: "Default General get request"
        def consentValue = new TcfConsent.Builder().build().toString()
        def accountId = PBSUtils.randomNumber.toString()
        def generalGetRequest = (consentGeneralRequest(consentValue) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
            it.accountId = accountId
            it.debug = DebugCondition.ENABLED
            it.consentType = TCF_2
            it.gdpr = 1
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(request.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes general get request"
        def response = privacyPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Generic bidderRequest should contain tcfv2 info"
        def bidderRequest = response.ext.debug.resolvedRequest
        verifyAll (bidderRequest) {
            it.user.consent == consentValue
            it.regs.gdpr == 1
        }

        and: "Shouldn't contain other privacy info"
        verifyAll (bidderRequest.regs) {
            !it.coppa
            !it.gpc
            !it.usPrivacy
            !it.gpp
        }

        and: "Bidder request shouldn't contain regs.ext"
        assert bidderRequest.regs.ext == new RegsExt()

        and: "PBS should cancel request"
        assert !bidder.getBidderRequests(request.id)

        and: "General Get Request with TCF_2 type correctly updates privacy enforcement metrics"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(request, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(request, FETCH_BIDS)] == 1

        where:
        consentGeneralRequest <<
                [
                        { String gppConsent -> new GeneralGetRequest(tcfConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsent: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent) },
                        { String gppConsent -> new GeneralGetRequest(tcfConsent: gppConsent, generalConsent: PBSUtils.randomString) },
                        { String gppConsent -> new GeneralGetRequest(tcfConsent: gppConsent, generalConsentString: PBSUtils.randomString) },
                        { String gppConsent -> new GeneralGetRequest(generalConsentString: gppConsent, generalConsent: PBSUtils.randomString) }
                ]
    }
}
