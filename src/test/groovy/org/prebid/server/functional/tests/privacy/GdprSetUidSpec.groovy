package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.util.ResourceUtil

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENER_X
import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.PurposeEnforcement.FULL
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.request.setuid.UidWithExpiry.getDefaultUidWithExpiry
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVICE_ACCESS

class GdprSetUidSpec extends PrivacyBaseSpec {

    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final Map<String, String> VENDOR_GENERIC_PBS_CONFIG = GENERIC_VENDOR_CONFIG +
            ["gdpr.purposes.p1.enforce-purpose"                                       : NO.value,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
             "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final String TCF_ERROR_MESSAGE = "The gdpr_consent param prevents cookies from being saved"
    private static final int UNAVAILABLE_FOR_LEGAL_REASONS_CODE = 451

    private static final PrebidServerService prebidServerService = pbsServiceFactory.getService(VENDOR_GENERIC_PBS_CONFIG)

    def "PBS setuid shouldn't failed with tcf when purpose access device not enforced"() {
        given: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: NO)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain tempUids cookie and headers"
        assert response.headers.size() == 7
        assert response.uidsCookie.tempUIDs[GENERIC].uid == setuidRequest.uid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")
    }

    def "PBS setuid shouldn't failed with tcf when bidder name and cookie-family-name mismatching"() {
        given: "PBS with different cookie-family-name"
        def pbsConfig = VENDOR_GENERIC_PBS_CONFIG +
                ["adapters.${GENERIC.value}.usersync.cookie-family-name": GENER_X.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENER_X
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENER_X): defaultUidWithExpiry]
        }

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: NO)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain tempUids cookie and headers"
        assert response.headers.size() == 7
        assert response.uidsCookie.tempUIDs[GENER_X].uid == setuidRequest.uid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid should failed with tcf when dgpr value is invalid"() {
        given: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([PBSUtils.getRandomNumberWithExclusion(GENERIC_VENDOR_ID, 0, 65534)])
                    .build()
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: NO)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)


        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAVAILABLE_FOR_LEGAL_REASONS_CODE
        assert exception.responseBody == TCF_ERROR_MESSAGE

        and: "Metric should be increased usersync.FAMILY.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["usersync.${GENERIC.value}.tcf.blocked"] == 1
    }

    def "PBS setuid should failed with tcf when purpose access device enforced for account"() {
        given: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: FULL)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAVAILABLE_FOR_LEGAL_REASONS_CODE
        assert exception.responseBody == TCF_ERROR_MESSAGE

        and: "Metric should be increased usersync.FAMILY.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["usersync.${GENERIC.value}.tcf.blocked"] == 1
    }

    def "PBS setuid should failed with tcf when purpose access device enforced for host"() {
        given: "PBS config"
        def pbsConfig = VENDOR_GENERIC_PBS_CONFIG + ["gdpr.purposes.p1.enforce-purpose": FULL.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: NO)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAVAILABLE_FOR_LEGAL_REASONS_CODE
        assert exception.responseBody == TCF_ERROR_MESSAGE

        and: "Metric should be increased usersync.FAMILY.tcf.blocked"
        def metric = prebidServerService.sendCollectedMetricsRequest()
        assert metric["usersync.${GENERIC.value}.tcf.blocked"] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid shouldn't failed with tcf when purpose access device not enforced for host and host-vendor-id empty"() {
        given: "PBS config"
        def pbsConfig = VENDOR_GENERIC_PBS_CONFIG + ["gdpr.purposes.p1.enforce-purpose": NO.value,
                                                     "gdpr.host-vendor-id"             : ""]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Save account config with purpose into DB"
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): new PurposeConfig(enforcePurpose: NO)], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain tempUids cookie and headers"
        assert response.headers.size() == 7
        assert response.uidsCookie.tempUIDs[GENERIC].uid == setuidRequest.uid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS setuid shouldn't failed with purpose access device enforced for account when bidder included in vendorExceptions"() {
        given: "Default setuid request with account"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = PBSUtils.randomNumber.toString()
            it.uid = UUID.randomUUID().toString()
            it.bidder = GENERIC
            it.gdpr = "1"
            it.gdprConsent = new TcfConsent.Builder()
                    .setPurposesLITransparency(DEVICE_ACCESS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
        }

        and: "Default uids cookie with rubicon bidder"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            it.tempUIDs = [(GENERIC): defaultUidWithExpiry]
        }

        and: "Save account config with purpose into DB"
        def purposeConfig = new PurposeConfig(enforcePurpose: FULL, vendorExceptions: [GENERIC.value])
        def accountConfig = new AccountConfig(
                auction: new AccountAuctionConfig(debugAllow: true),
                privacy: new AccountPrivacyConfig(gdpr: new AccountGdprConfig(purposes: [(P1): purposeConfig], enabled: true)))
        def account = new Account(status: ACTIVE, uuid: setuidRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain tempUids cookie and headers"
        assert response.headers.size() == 7
        assert response.uidsCookie.tempUIDs[GENERIC].uid == setuidRequest.uid
        assert response.responseBody ==
                ResourceUtil.readByteArrayFromClassPath("org/prebid/server/functional/tracking-pixel.png")
    }
}
