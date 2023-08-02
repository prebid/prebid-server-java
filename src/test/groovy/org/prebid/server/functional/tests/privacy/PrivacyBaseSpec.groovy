package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PbsPgConfig
import org.prebid.server.functional.testcontainers.scaffolding.VendorList
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.ConsentString
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.util.privacy.gpp.UspNatV1Consent
import spock.lang.Shared

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_2
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

abstract class PrivacyBaseSpec extends BaseSpec {

    private static final int GEO_PRECISION = 2
    @Shared
    protected final PrebidServerService privacyPbsService = pbsServiceFactory.getService(GDPR_VENDOR_LIST_CONFIG +
            ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String])

    private static final Map<String, String> GENERIC_COOKIE_SYNC_CONFIG = ["adapters.${GENERIC.value}.usersync.${REDIRECT.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync".toString(),
                                                                           "adapters.${GENERIC.value}.usersync.${REDIRECT.value}.support-cors": false.toString()]
    private static final Map<String, String> OPENX_COOKIE_SYNC_CONFIG = ["adaptrs.${OPENX.value}.enabled"                     : "true",
                                                                         "adapters.${OPENX.value}.usersync.cookie-family-name": OPENX.value]
    private static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX.value}.endpoint": "$networkServiceContainer.rootUri/auction".toString(),
                                                             "adapters.${OPENX.value}.enabled" : 'true']
    private static final Map<String, String> GDPR_VENDOR_LIST_CONFIG = ["gdpr.vendorlist.v2.http-endpoint-template": "$networkServiceContainer.rootUri/v2/vendor-list.json".toString(),
                                                                        "gdpr.vendorlist.v3.http-endpoint-template": "$networkServiceContainer.rootUri/v3/vendor-list.json".toString()]
    private static final Map<String, String> SETTING_CONFIG = ["settings.enforce-valid-account": 'true']
    private static final PbsPgConfig pgConfig = new PbsPgConfig(networkServiceContainer)

    protected static final Map<String, String> PBS_CONFIG = OPENX_CONFIG + OPENX_COOKIE_SYNC_CONFIG +
            GENERIC_COOKIE_SYNC_CONFIG + pgConfig.properties + GDPR_VENDOR_LIST_CONFIG + SETTING_CONFIG
    protected static final String VALID_VALUE_FOR_GPC_HEADER = "1"
    protected static final GppConsent SIMPLE_GPC_DISALLOW_LOGIC = new UspNatV1Consent.Builder().setGpc(true).build()
    protected static final VendorList vendorListResponse = new VendorList(networkServiceContainer)

    @Shared
    protected PrebidServerService activityPbsService = pbsServiceFactory.getService(PBS_CONFIG)

    void setup() {
        vendorListResponse.setResponse()
    }

    void cleanup() {
        vendorListResponse.reset()
    }

    protected static BidRequest getBidRequestWithGeo(DistributionChannel channel = SITE) {
        BidRequest.getDefaultBidRequest(channel).tap {
            device = new Device(ip: "43.77.114.227", ipv6: "af47:892b:3e98:b49a:a747:bda4:a6c8:aee2",
                    geo: new Geo(lat: PBSUtils.getRandomDecimal(0, 90), lon: PBSUtils.getRandomDecimal(0, 90)))
            user = User.defaultUser.tap {
                geo = new Geo(lat: PBSUtils.getRandomDecimal(0, 90), lon: PBSUtils.getRandomDecimal(0, 90))
            }
        }
    }

    protected static BidRequest getStoredRequestWithGeo() {
        BidRequest.defaultStoredRequest.tap {
            device = new Device(geo: new Geo(lat: PBSUtils.getRandomDecimal(0, 90), lon: PBSUtils.getRandomDecimal(0, 90)))
        }
    }

    protected static BidRequest getCcpaBidRequest(DistributionChannel channel = SITE, ConsentString consentString) {
        getBidRequestWithGeo(channel).tap {
            regs.ext = new RegsExt(usPrivacy: consentString)
        }
    }

    protected static BidRequest getGdprBidRequest(DistributionChannel channel = SITE, ConsentString consentString) {
        getBidRequestWithGeo(channel).tap {
            regs.ext = new RegsExt(gdpr: 1)
            user = new User(ext: new UserExt(consent: consentString))
        }
    }

    protected static AmpRequest getCcpaAmpRequest(ConsentString consentStringVal) {
        AmpRequest.defaultAmpRequest.tap {
            consentString = consentStringVal
            consentType = US_PRIVACY
        }
    }

    protected static AmpRequest getGdprAmpRequest(ConsentString consentString) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = consentString
            consentType = TCF_2
            gdprApplies = true
            timeout = 5000
        }
    }

    protected static AmpRequest getGppAmpRequest(String consentString,
                                                 String gppSid = null,
                                                 ConsentType consentType = GPP) {
        AmpRequest.defaultAmpRequest.tap {
            it.consentString = consentString
            it.gppSid = gppSid
            it.consentType = consentType
        }
    }

    protected static Geo maskGeo(BidRequest bidRequest, int precision = GEO_PRECISION) {
        def geo = bidRequest.device.geo.clone()
        geo.lat = PBSUtils.roundDecimal(bidRequest.device.geo.lat as BigDecimal, precision)
        geo.lon = PBSUtils.roundDecimal(bidRequest.device.geo.lon as BigDecimal, precision)
        geo
    }

    protected static void cacheVendorList(PrebidServerService pbsService) {
        def isVendorListCachedClosure = {
            def validConsentString = new TcfConsent.Builder()
                    .setPurposesLITransparency(BASIC_ADS)
                    .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()
            def bidRequest = getGdprBidRequest(validConsentString)

            pbsService.sendAuctionRequest(bidRequest)

            pbsService.sendCollectedMetricsRequest()["privacy.tcf.v2.vendorlist.missing"] == 0
        }
        PBSUtils.waitUntil(isVendorListCachedClosure, 10000, 1000)
    }

    protected static Account getAccountWithGdpr(String accountId, AccountGdprConfig gdprConfig) {
        getAccountWithPrivacy(accountId, new AccountPrivacyConfig(gdpr: gdprConfig))
    }

    protected static Account getAccountWithCcpa(String accountId, AccountCcpaConfig ccpaConfig) {
        getAccountWithPrivacy(accountId, new AccountPrivacyConfig(ccpa: ccpaConfig))
    }

    private static Account getAccountWithPrivacy(String accountId, AccountPrivacyConfig privacy) {
        new Account(uuid: accountId, config: new AccountConfig(privacy: privacy))
    }

    protected static Account getAccountWithAllowActivitiesAndPrivacyModule(String accountId,
                                                                           AllowActivities activities,
                                                                           List<AccountGppConfig> gppConfigs = []) {

        def privacy = new AccountPrivacyConfig(ccpa: new AccountCcpaConfig(enabled: true), allowActivities: activities, modules: gppConfigs)
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false))
        def accountConfig = new AccountConfig(cookieSync: cookieSyncConfig, privacy: privacy)
        new Account(uuid: accountId, config: accountConfig)
    }
}
