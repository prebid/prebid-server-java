package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.request.auction.Eid

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.util.privacy.TcfConsent.Builder
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.AUDIENCE_MARKET_RESEARCH
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVELOPMENT_IMPROVE_PRODUCTS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVICE_ACCESS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.MEASURE_AD_PERFORMANCE
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.MEASURE_CONTENT_PERFORMANCE
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.PERSONALIZED_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.PERSONALIZED_ADS_PROFILE
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.PERSONALIZED_CONTENT
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.PERSONALIZED_CONTENT_PROFILE

class TcfTransmitEidsActivitiesSpec extends PrivacyBaseSpec {

    def "PBS should leave the original request with eids data when requireConsent is enabled and P4 have consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = purposeConfig.tap {
            eid = new PurposeEid(requireConsent: true)
        }
        def accountGdprConfig = new AccountGdprConfig(purposes: [(Purpose.P4): purposeConfigWithRequiredConsent])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        purposeConfig                                                                   | tcfConsent
        new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_ADS).build()
        new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_ADS).build()
        new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_ADS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #purposeVersion have consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = new PurposeConfig(eid: new PurposeEid(requireConsent: true))
        def purposes = [(Purpose.P4): purposeConfigWithRequiredConsent, (purposeVersion): purposeConfig]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        purposeVersion | purposeConfig                                                                   | tcfConsent
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P2     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(BASIC_ADS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P3     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_ADS_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P5     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P6     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P7     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_AD_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P8     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_CONTENT_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P9     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(AUDIENCE_MARKET_RESEARCH)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P10    | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #purposeVersion have consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = new PurposeConfig(eid: new PurposeEid(requireConsent: true, exceptions: [GENERIC.value]))
        def purposes = [(Purpose.P4): purposeConfigWithRequiredConsent, (purposeVersion): purposeConfig]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        purposeVersion | purposeConfig                                                                   | tcfConsent
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P2     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(BASIC_ADS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P3     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_ADS_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P5     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P6     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P7     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_AD_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P8     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_CONTENT_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P9     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(AUDIENCE_MARKET_RESEARCH)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P10    | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #purposeVersion have unsupported consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = new PurposeConfig(eid: new PurposeEid(requireConsent: true, exceptions: [GENERIC.value]))
        def purposes = [(Purpose.P4): purposeConfigWithRequiredConsent, (purposeVersion): purposeConfig]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        purposeVersion | purposeConfig                                                                   | tcfConsent
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(MEASURE_AD_PERFORMANCE).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_CONTENT).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_ADS).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P1     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(DEVICE_ACCESS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #purposeVersion have consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = new PurposeConfig(eid: new PurposeEid(requireConsent: false))
        def purposes = [(Purpose.P4): purposeConfigWithRequiredConsent, (purposeVersion): purposeConfig]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        purposeVersion | purposeConfig                                                                   | tcfConsent
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P2     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(BASIC_ADS).build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P2     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(BASIC_ADS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P3     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_ADS_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false,
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().setPurposesConsent(PERSONALIZED_ADS).build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true,
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false,
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().build()
        Purpose.P4     | new PurposeConfig(vendorExceptions: [GENERIC.value],
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value],
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().setPurposesConsent(PERSONALIZED_ADS).build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value],
                eid: new PurposeEid(requireConsent: false))                                              | new Builder().build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true,
                eid: new PurposeEid(requireConsent: false))                                              | new Builder()
                .setPurposesConsent(PERSONALIZED_ADS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P5     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT_PROFILE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P6     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(PERSONALIZED_CONTENT).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(PERSONALIZED_CONTENT)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P7     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_AD_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_AD_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P8     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(MEASURE_CONTENT_PERFORMANCE)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P9     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(AUDIENCE_MARKET_RESEARCH)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P10    | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(DEVELOPMENT_IMPROVE_PRODUCTS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and purpose #purposeVersion have unsupported consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposeConfigWithRequiredConsent = new PurposeConfig(eid: new PurposeEid(requireConsent: false))
        def purposes = [(Purpose.P4): purposeConfigWithRequiredConsent, (purposeVersion): purposeConfig]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        purposeVersion | purposeConfig                                                                   | tcfConsent
        Purpose.P10    | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(DEVELOPMENT_IMPROVE_PRODUCTS).build()
        Purpose.P9     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(AUDIENCE_MARKET_RESEARCH).build()
        Purpose.P8     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(MEASURE_CONTENT_PERFORMANCE).build()
        Purpose.P7     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(MEASURE_AD_PERFORMANCE).build()
        Purpose.P6     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_CONTENT).build()
        Purpose.P5     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_CONTENT_PROFILE).build()
        Purpose.P4     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_ADS).build()
        Purpose.P3     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(PERSONALIZED_ADS_PROFILE).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder().setPurposesLITransparency(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: false)                 | new Builder().setPurposesConsent(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, enforceVendors: true)                     | new Builder().setVendorConsent(GENERIC_VENDOR_ID).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, enforceVendors: false)                    | new Builder().build()
        Purpose.P1     | new PurposeConfig(vendorExceptions: [GENERIC.value])                            | new Builder().build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, softVendorExceptions: [GENERIC.value]) | new Builder().setPurposesConsent(DEVICE_ACCESS).build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: NO, softVendorExceptions: [GENERIC.value])    | new Builder().build()
        Purpose.P1     | new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)                  | new Builder()
                .setPurposesConsent(DEVICE_ACCESS)
                .setVendorConsent(GENERIC_VENDOR_ID)
                .build()
    }
}
