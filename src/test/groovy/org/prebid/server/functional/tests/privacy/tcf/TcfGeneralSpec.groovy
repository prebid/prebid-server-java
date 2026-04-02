package org.prebid.server.functional.tests.privacy.tcf


import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.model.response.auction.NoBidResponse
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfUtils

import java.time.ZoneId
import java.time.ZonedDateTime

import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfGeneralSpec extends TcfBaseSpec {

    private static final ZonedDateTime TCF_2_3_ENFORCEMENT_DATE = ZonedDateTime.parse("2026-03-01T00:00:00Z")

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

        when: "PBS processes auction requests"
        def response = activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS should emit proper nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should sent bid request to bidder"
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

        when: "PBS processes auction requests"
        def response = privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS should emit proper nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should sent bid request to bidder"
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

        when: "PBS processes auction requests"
        def response = activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS should emit proper nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should sent bid request to bidder"
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

        when: "PBS processes auction requests"
        def response = privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Response should contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.size() == 1

        and: "PBS should emit proper nbr code"
        assert !response.noBidResponse

        and: "Response shouldn't contain errors or warnings"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should sent bid request to bidder"
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

        when: "PBS processes auction requests"
        def response = activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == ['Invalid TCF string: `disclosedVendors` list is empty.']

        and: "PBS should not send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).isEmpty()

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

        when: "PBS processes auction requests"
        def response = privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == ['Invalid TCF string: `disclosedVendors` list is empty.']

        and: "PBS should not send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).isEmpty()

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
        def response = activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should not send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).isEmpty()
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
        def response = privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should not send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).isEmpty()
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
        def response = activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain seatBid"
        assert response?.seatbid?.bid?.flatten()?.isEmpty()

        and: "PBS should emit proper nbr code"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR

        and: "PBS response shouldn't provide proper error"
        assert !response.ext.errors

        and: "PBS response should include warnings"
        assert response.ext.warnings[PREBID].message == ['Invalid TCF string: `disclosedVendors` list is empty.']

        and: "PBS should not send bid request to bidder"
        assert bidder.getBidderRequests(bidRequest.id).isEmpty()

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

    private static Account generateDefaultTcfAccount(String accountId, EnforcementRequirement enforcementRequirements) {
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        getAccountWithGdpr(accountId, accountGdprConfig)
    }
}
