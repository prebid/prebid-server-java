package org.prebid.server.functional.tests.privacy.tcf

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.model.response.auction.NoBidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.TcfUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfGeneralSpec extends TcfBaseSpec {

    private final static ZonedDateTime TCF_2_3_ENFORCEMENT_DATE = ZonedDateTime.parse("2026-03-01T00:00:00Z")
    private final static Map<String, String> STRICT_DISCLOSED_VENDOR_TREATMENT_CONFIG = TCF_BASE_CONFIG + ["gdpr.strict-disclosed-vendors-treatment": 'true']
    private final static TCF_NO_DISCLOSED_VENDORS_METRIC = 'privacy.tcf.no-disclosed-vendors'
    private final static DISCLOSED_VENDORS_MESSAGE = 'Invalid TCF string: `disclosedVendors` list is empty.'

    private static PrebidServerService pbsWithoutGvlVendorsWithStrictDvTreatment
    private static PrebidServerService pbsWithMultipleGvlListsWithStrictDvTreatment

    def setupSpec() {
        pbsWithoutGvlVendorsWithStrictDvTreatment = pbsServiceFactory.getService(STRICT_DISCLOSED_VENDOR_TREATMENT_CONFIG + VENDOR_LIST_EMPTY_CONFIG)
        pbsWithMultipleGvlListsWithStrictDvTreatment = pbsServiceFactory.getService(STRICT_DISCLOSED_VENDOR_TREATMENT_CONFIG, GLV_LISTS_FILES)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(STRICT_DISCLOSED_VENDOR_TREATMENT_CONFIG + VENDOR_LIST_EMPTY_CONFIG)
        pbsServiceFactory.removeContainer(STRICT_DISCLOSED_VENDOR_TREATMENT_CONFIG)
    }

    def "PBS should accept base consent when disclosedVendors includes vendor after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with valid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultBase(GENERIC_VENDOR_ID).tap {
            it.created = TCF_2_3_ENFORCEMENT_DATE
            it.updated = TCF_2_3_ENFORCEMENT_DATE
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithoutGvlVendorsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithoutGvlVendorsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS shouldn't emit nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS shouldn't emit no disclosed vendors metric"
        def metrics = pbsWithoutGvlVendorsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert !metrics[TCF_NO_DISCLOSED_VENDORS_METRIC]

        and: "PBS should send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).size() == 1
    }

    def "PBS should accept full consent when disclosedVendors includes vendor after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with valid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultFull(GENERIC_VENDOR_ID, PURPOSES_ONLY_GVL_VERSION).tap {
            it.created = TCF_2_3_ENFORCEMENT_DATE
            it.updated = TCF_2_3_ENFORCEMENT_DATE
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithMultipleGvlListsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithMultipleGvlListsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS shouldn't emit nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS shouldn't emit no disclosed vendors metric"
        def metrics = pbsWithMultipleGvlListsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert !metrics[TCF_NO_DISCLOSED_VENDORS_METRIC]

        and: "PBS should send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).size() == 1
    }

    def "PBS should accept base consent regardless of disclosedVendors before TCF v2.3 enforcement"() {
        given: "Generic BidRequests with valid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultBase(GENERIC_VENDOR_ID).tap {
            it.created = TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)
            it.updated = TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)
            it.disclosedVendorsId = disclosedIds
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithoutGvlVendorsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithoutGvlVendorsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS shouldn't emit nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS shouldn't emit no disclosed vendors metric"
        def metrics = pbsWithoutGvlVendorsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert !metrics[TCF_NO_DISCLOSED_VENDORS_METRIC]

        and: "PBS should send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).size() == 1

        where:
        disclosedIds << [null, [], [PBSUtils.getRandomNumber(0, 65535)]]
    }

    def "PBS should accept full consent regardless of disclosedVendors before TCF v2.3 enforcement"() {
        given: "Generic BidRequests with valid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultFull(GENERIC_VENDOR_ID, PURPOSES_ONLY_GVL_VERSION).tap {
            it.created = TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)
            it.updated = TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)
            it.disclosedVendorsId = disclosedIds
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithMultipleGvlListsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithMultipleGvlListsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS shouldn't emit nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS shouldn't emit no disclosed vendors metric"
        def metrics = pbsWithMultipleGvlListsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert !metrics[TCF_NO_DISCLOSED_VENDORS_METRIC]

        and: "PBS should send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).size() == 1

        where:
        disclosedIds << [null, [], [PBSUtils.getRandomNumber(0, 65535)]]
    }

    def "PBS should reject base consent with warning when disclosedVendors is empty after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with invalid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultBase(GENERIC_VENDOR_ID).tap {
            it.disclosedVendorsId = disclosedIds
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithoutGvlVendorsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithoutGvlVendorsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == [DISCLOSED_VENDORS_MESSAGE]

        and: "PBS should emit no disclosed vendors metric"
        def metrics = pbsWithoutGvlVendorsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert metrics[TCF_NO_DISCLOSED_VENDORS_METRIC] == 1

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        disclosedIds << [null, []]
    }

    def "PBS should reject full consent with warning when disclosedVendors is empty after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with invalid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultFull(GENERIC_VENDOR_ID, PURPOSES_ONLY_GVL_VERSION).tap {
            it.disclosedVendorsId = disclosedIds
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsWithMultipleGvlListsWithStrictDvTreatment)

        when: "PBS processes auction requests"
        def response = pbsWithMultipleGvlListsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == [DISCLOSED_VENDORS_MESSAGE]

        and: "PBS should emit no disclosed vendors metric"
        def metrics = pbsWithMultipleGvlListsWithStrictDvTreatment.sendCollectedMetricsRequest()
        assert metrics[TCF_NO_DISCLOSED_VENDORS_METRIC] == 1

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        disclosedIds << [null, []]
    }

    def "PBS should reject base consent without warning when disclosedVendors does not match vendor after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with non-corresponding TCF strings"
        def enforcementRequirements = EnforcementRequirement.getDefaultBase(GENERIC_VENDOR_ID).tap {
            it.disclosedVendorsId = [PBSUtils.getRandomNumber(0, 65535)]
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = pbsWithoutGvlVendorsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should reject full consent without warning when disclosedVendors does not match vendor after TCF v2.3 enforcement"() {
        given: "Generic BidRequests with non-corresponding TCF strings"
        def enforcementRequirements = EnforcementRequirement.getDefaultFull(GENERIC_VENDOR_ID, PURPOSES_ONLY_GVL_VERSION).tap {
            it.disclosedVendorsId = [PBSUtils.getRandomNumber(0, 65535)]
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = pbsWithMultipleGvlListsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)
    }

    def "PBS should use latest UTC timestamp between created and updated for processing TCF string"() {
        given: "Generic BidRequests with invalid tcf string"
        def enforcementRequirements = EnforcementRequirement.getDefaultBase(GENERIC_VENDOR_ID).tap {
            it.created = createDate
            it.updated = updateDate
            it.disclosedVendorsId = null
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = pbsWithoutGvlVendorsWithStrictDvTreatment.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == [DISCLOSED_VENDORS_MESSAGE]

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        createDate                                                                                  | updateDate
        TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)                                                    | TCF_2_3_ENFORCEMENT_DATE
        TCF_2_3_ENFORCEMENT_DATE                                                                    | TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)

        TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1).withZoneSameInstant(ZoneId.of("Pacific/Honolulu")) | TCF_2_3_ENFORCEMENT_DATE
        TCF_2_3_ENFORCEMENT_DATE                                                                    | TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1).withZoneSameInstant(ZoneId.of("Pacific/Honolulu"))

        TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)                                                    | TCF_2_3_ENFORCEMENT_DATE.withZoneSameInstant(ZoneId.of("Pacific/Kiritimati"))
        TCF_2_3_ENFORCEMENT_DATE.withZoneSameInstant(ZoneId.of("Pacific/Kiritimati"))               | TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1)

        TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1).withZoneSameInstant(ZoneId.of("Pacific/Honolulu")) | TCF_2_3_ENFORCEMENT_DATE.withZoneSameInstant(ZoneId.of("Pacific/Kiritimati"))
        TCF_2_3_ENFORCEMENT_DATE.withZoneSameInstant(ZoneId.of("Pacific/Kiritimati"))               | TCF_2_3_ENFORCEMENT_DATE.minusSeconds(1).withZoneSameInstant(ZoneId.of("Pacific/Honolulu"))
    }

    def "PBS should allow auction when disclosed vendors are empty under strict enforcement #serviceName"() {
        given: "Generic BidRequests with invalid tcf string"
        def enforcementRequirements = new EnforcementRequirement().tap {
            it.disclosedVendorsId = null
            it.created = null
            it.updated = null
            it.purpose = Purpose.P2
            it.vendorExceptions = [BidderName.GENERIC]
        }
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Save account config with requireConsent into DB"
        def account = generateDefaultTcfAccount(bidRequest.accountId, enforcementRequirements)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsService)

        when: "PBS processes auction requests"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS shouldn't emit nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == [DISCLOSED_VENDORS_MESSAGE]

        and: "PBS should emit no disclosed vendors metric"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics[TCF_NO_DISCLOSED_VENDORS_METRIC] == 1

        where:
        pbsService                                   | serviceName
        pbsWithoutGvlVendorsWithStrictDvTreatment    | "without GVL vendors"
        pbsWithMultipleGvlListsWithStrictDvTreatment | "with multiple GVL lists"
    }

    def "PBS should reject auction for deprecated TCF v1 consent regardless of disclosed vendors configuration #serviceName"() {
        given: "BidRequest with deprecated TCF v1 consent"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesConsent(TcfConsent.PurposeId.BASIC_ADS)
                .setVersion(1)
                .setDisclosedVendors([GENERIC_VENDOR_ID])
                .setCreateTime(ZonedDateTime.now())
                .setUpdatedTime(ZonedDateTime.now())
                .build()
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Account GDPR config with disabled vendor enforcement"
        def purposes = [(Purpose.P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(pbsService)

        when: "PBS processes auction requests"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS should contain TCF deprecation warning"
        assert response.ext.warnings[PREBID].message == ["Parsing consent string:\"${tcfConsent}\" failed. " +
                                                                 "TCF version 1 is deprecated and treated as corrupted TCF version 2"]

        and: "PBS should not send bid request to bidder"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        pbsService                                   | serviceName
        pbsWithoutGvlVendorsWithStrictDvTreatment    | "without GVL vendors"
        pbsWithMultipleGvlListsWithStrictDvTreatment | "with multiple GVL lists"
    }
}
