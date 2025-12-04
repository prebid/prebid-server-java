package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.config.AccountDsaConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.model.privacy.gpp.GppDataActivity
import org.prebid.server.functional.model.privacy.gpp.UsCaliforniaV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsCaliforniaV1SensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsColoradoV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsColoradoV1SensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsConnecticutV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsConnecticutV1SensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsUtahV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsUtahV1SensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsVirginiaV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsVirginiaV1SensitiveData
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.amp.ConsentType
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.GeoExt
import org.prebid.server.functional.model.request.auction.GeoExtGeoProvider
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.VendorList
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.ConsentString
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.util.privacy.gpp.v1.UsCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsVaV1Consent

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.FULL
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.getDefaultVendorListResponse
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.pricefloors.Country.BULGARIA
import static org.prebid.server.functional.model.request.GppSectionId.US_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_VA_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_2
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.REQUIRE_CONSENT
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.REQUIRE_LEGITIMATE_INTEREST
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.UNDEFINED
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA

abstract class PrivacyBaseSpec extends BaseSpec {

    private static final int GEO_PRECISION = 2

    protected static final Map<String, String> GENERIC_CONFIG = ["adapters.${GENERIC.value}.usersync.${REDIRECT.value}.url"         : "$networkServiceContainer.rootUri/generic-usersync".toString(),
                                                                 "adapters.${GENERIC.value}.usersync.${REDIRECT.value}.support-cors": false.toString(),
                                                                 "adapters.${GENERIC.value}.ortb-version"                           : "2.6"]
    private static final Map<String, String> OPENX_CONFIG = ["adaptrs.${OPENX.value}.enabled"                     : "true",
                                                             "adapters.${OPENX.value}.usersync.cookie-family-name": OPENX.value,
                                                             "adapters.${OPENX}.ortb-version"                     : "2.6",
                                                             "adapters.${OPENX.value}.endpoint"                   : "$networkServiceContainer.rootUri/auction".toString(),
                                                             "adapters.${OPENX.value}.enabled"                    : 'true']
    protected static final Map<String, String> GDPR_VENDOR_LIST_CONFIG = ["gdpr.vendorlist.v2.http-endpoint-template": "$networkServiceContainer.rootUri/v2/vendor-list.json".toString(),
                                                                          "gdpr.vendorlist.v3.http-endpoint-template": "$networkServiceContainer.rootUri/v3/vendor-list.json".toString()]
    protected static final Map<String, String> SETTING_CONFIG = ["settings.enforce-valid-account": 'true']
    protected static final Map<String, String> GENERIC_VENDOR_CONFIG = ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String,
                                                                        "gdpr.host-vendor-id"                 : GENERIC_VENDOR_ID as String,
                                                                        "adapters.generic.ccpa-enforced"      : "true"]

    protected static final int PURPOSES_ONLY_GVL_VERSION = PBSUtils.getRandomNumber(0, 4095)
    protected static final int LEG_INT_PURPOSES_ONLY_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion(PURPOSES_ONLY_GVL_VERSION, 0, 4095)
    protected static final int LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion([PURPOSES_ONLY_GVL_VERSION, LEG_INT_PURPOSES_ONLY_GVL_VERSION], 0, 4095)
    protected static final int PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion([PURPOSES_ONLY_GVL_VERSION, LEG_INT_PURPOSES_ONLY_GVL_VERSION, LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION], 0, 4095)

    protected static final int EXPONENTIAL_BACKOFF_MAX_DELAY = 1

    private static final Map<String, String> RETRY_POLICY_EXPONENTIAL_CONFIG = [
            "gdpr.vendorlist.v2.retry-policy.exponential-backoff.delay-millis"    : 1 as String,
            "gdpr.vendorlist.v2.retry-policy.exponential-backoff.max-delay-millis": EXPONENTIAL_BACKOFF_MAX_DELAY as String,
            "gdpr.vendorlist.v2.retry-policy.exponential-backoff.factor"          : Long.MAX_VALUE as String,
            "gdpr.vendorlist.v3.retry-policy.exponential-backoff.delay-millis"    : 1 as String,
            "gdpr.vendorlist.v3.retry-policy.exponential-backoff.max-delay-millis": EXPONENTIAL_BACKOFF_MAX_DELAY as String,
            "gdpr.vendorlist.v3.retry-policy.exponential-backoff.factor"          : Long.MAX_VALUE as String]

    private static final Map<String, String> GDPR_EEA_COUNTRY = ["gdpr.eea-countries": "$BULGARIA.ISOAlpha2, SK, VK" as String]

    protected static final String VENDOR_LIST_PATH = "/app/prebid-server/data/vendorlist-v{VendorVersion}/{VendorVersion}.json"
    protected static final String INVALID_GPP_SEGMENT = PBSUtils.getRandomString(7)
    protected static final String INVALID_GPP_STRING = "DBABLA~${INVALID_GPP_SEGMENT}.YA"
    protected static final String VALID_VALUE_FOR_GPC_HEADER = "1"
    protected static final GppConsent SIMPLE_GPC_DISALLOW_LOGIC = new UsNatV1Consent.Builder().setGpc(true).build()
    protected static final VendorList vendorListResponse = new VendorList(networkServiceContainer)
    protected static final Integer MAX_INVALID_TCF_POLICY_VERSION = 63
    protected static final Integer MIN_INVALID_TCF_POLICY_VERSION = 6

    protected static final Map<String, String> GENERAL_PRIVACY_CONFIG =
            GENERIC_CONFIG + GDPR_VENDOR_LIST_CONFIG + GENERIC_VENDOR_CONFIG + RETRY_POLICY_EXPONENTIAL_CONFIG

    protected static PrebidServerService privacyPbsService
    protected static PrebidServerService activityPbsService

    def setupSpec() {
        privacyPbsService = pbsServiceFactory.getService(GENERAL_PRIVACY_CONFIG + GDPR_EEA_COUNTRY)
        activityPbsService = pbsServiceFactory.getService(OPENX_CONFIG + SETTING_CONFIG + GENERAL_PRIVACY_CONFIG)
        vendorListResponse.setResponse()
    }

    def cleanupSpec() {
        vendorListResponse.reset()
    }

    protected static BidRequest getBidRequestWithGeo(DistributionChannel channel = SITE) {
        BidRequest.getDefaultBidRequest(channel).tap {
            device = new Device(
                    ip: "43.77.114.227",
                    ipv6: "af47:892b:3e98:b49a:a747:bda4:a6c8:aee2",
                    geo: new Geo(
                            lat: PBSUtils.getRandomDecimal(0, 90),
                            lon: PBSUtils.getRandomDecimal(0, 90),
                            country: USA,
                            region: ALABAMA,
                            utcoffset: PBSUtils.randomNumber,
                            metro: PBSUtils.randomString,
                            city: PBSUtils.randomString,
                            zip: PBSUtils.randomString,
                            accuracy: PBSUtils.randomNumber,
                            ipservice: PBSUtils.randomNumber,
                            ext: new GeoExt(geoProvider: new GeoExtGeoProvider()),
                    ))
            user = User.defaultUser.tap {
                geo = new Geo(
                        lat: PBSUtils.getRandomDecimal(0, 90),
                        lon: PBSUtils.getRandomDecimal(0, 90))
            }
        }
    }

    protected static BidRequest getBidRequestWithPersonalData(String accountId = null, DistributionChannel channel = SITE) {
        getBidRequestWithGeo(channel).tap {
            if (accountId != null) {
                setAccountId(accountId)
            }
            ext.prebid.trace = VERBOSE
            device.tap {
                didsha1 = PBSUtils.randomString
                didmd5 = PBSUtils.randomString
                dpidsha1 = PBSUtils.randomString
                ifa = PBSUtils.randomString
                macsha1 = PBSUtils.randomString
                macmd5 = PBSUtils.randomString
                dpidmd5 = PBSUtils.randomString
            }
            user.tap {
                customdata = PBSUtils.randomString
                eids = [Eid.defaultEid]
                data = [new Data(name: PBSUtils.randomString)]
                buyeruid = PBSUtils.randomString
                yob = PBSUtils.randomNumber
                gender = PBSUtils.randomString
                geo = Geo.FPDGeo
                ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
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
            regs.usPrivacy = consentString
        }
    }

    protected static BidRequest getGdprBidRequest(DistributionChannel channel = SITE, ConsentString consentString) {
        getBidRequestWithGeo(channel).tap {
            regs.gdpr = 1
            user = new User(consent: consentString)
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
        geo.accuracy = null
        geo.zip = null
        geo.metro = null
        geo.city = null
        geo.ext = null
        geo.ipservice = null
        geo
    }

    protected static void cacheVendorList(PrebidServerService pbsService) {
        def isVendorListCachedClosure = {
            def validConsentString = new TcfConsent.Builder()
                    .setPurposesLITransparency(BASIC_ADS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
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

    protected static Account getAccountWithDsa(String accountId, AccountDsaConfig dsaConfig) {
        getAccountWithPrivacy(accountId, new AccountPrivacyConfig(dsa: dsaConfig))
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

    protected static String getVendorListPath(Integer gvlVersion) {
        "/app/prebid-server/data/vendorlist-v${TCF_POLICY_V2.vendorListVersion}/${gvlVersion}.json"
    }

    protected static List<EnforcementRequirement> getBasicTcfCompanyBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, enforceVendor: false),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID)
        ]
    }

    protected static List<EnforcementRequirement> getBasicTcfLegalBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirement(purpose: purpose, vendorExceptions: [GENERIC])
        ]
    }

    protected static List<EnforcementRequirement> getBasicTcfCompanySoftVendorExceptionsRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, vendorExceptions: [GENERIC]),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, vendorExceptions: [GENERIC])]
    }

    protected static List<EnforcementRequirement> getBasicTcfLegalPurposesLITEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: true)]
    }

    protected static List<EnforcementRequirement> getFullTcfLegalEnforcementRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: FULL,
                vendorConsentBitField: GENERIC_VENDOR_ID,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION)
        ]
    }

    protected static List<EnforcementRequirement> getFullTcfLegalEnforcementRequirementsRandomlyWithExcludePurpose(Purpose purpose) {
        getFullTcfLegalEnforcementRequirements(purpose, true)
    }

    protected static List<EnforcementRequirement> getFullTcfCompanyEnforcementRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: NO,
                enforceVendor: false,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: FULL,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 enforcePurpose: NO,
                 enforceVendor: false)]
    }

    protected static String generateSensitiveGpp(GppSectionId sectionId, Map<String, GppDataActivity> fieldsMap) {
        def sensitiveData
        def consentBuilder

        switch (sectionId) {
            case US_CA_V1:
                sensitiveData = new UsCaliforniaV1SensitiveData()
                consentBuilder = new UsCaV1Consent.Builder()
                break
            case US_VA_V1:
                sensitiveData = new UsVirginiaV1SensitiveData()
                consentBuilder = new UsVaV1Consent.Builder()
                break
            case US_CO_V1:
                sensitiveData = new UsColoradoV1SensitiveData()
                consentBuilder = new UsCoV1Consent.Builder()
                break
            case US_UT_V1:
                sensitiveData = new UsUtahV1SensitiveData()
                consentBuilder = new UsUtV1Consent.Builder()
                break
            case US_CT_V1:
                sensitiveData = new UsConnecticutV1SensitiveData()
                consentBuilder = new UsCtV1Consent.Builder()
                break
            default:
                throw new IllegalArgumentException("Unsupported Section ID for Sensitive Data: $sectionId")
        }

        fieldsMap.each { fieldName, value ->
            sensitiveData.setProperty("$fieldName", value)
        }

        consentBuilder.setSensitiveDataProcessing(sensitiveData).build().toString()
    }

    protected static String generateChildSensitiveGpp(GppSectionId sectionId, List<GppDataActivity> fields) {
        def childSensitiveData
        def consentBuilder

        switch (sectionId) {
            case US_CA_V1:
                childSensitiveData = UsCaliforniaV1ChildSensitiveData.getDefault(*fields)
                consentBuilder = new UsCaV1Consent.Builder()
                break

            case US_VA_V1:
                childSensitiveData = UsVirginiaV1ChildSensitiveData.getDefault(fields.first)
                consentBuilder = new UsVaV1Consent.Builder()
                break

            case US_CO_V1:
                childSensitiveData = UsColoradoV1ChildSensitiveData.getDefault(fields.first)
                consentBuilder = new UsCoV1Consent.Builder()
                break

            case US_UT_V1:
                childSensitiveData = UsUtahV1ChildSensitiveData.getDefault(fields.first)
                consentBuilder = new UsUtV1Consent.Builder()
                break

            case US_CT_V1:
                childSensitiveData = UsConnecticutV1ChildSensitiveData.getDefault(*fields)
                consentBuilder = new UsCtV1Consent.Builder()
                break

            default:
                throw new IllegalArgumentException("Unsupported Section ID for Child Data: $sectionId")
        }

        consentBuilder.setKnownChildSensitiveDataConsents(childSensitiveData).build().toString()
    }

    protected static List<EnforcementRequirement> getFullTcfCompanyEnforcementRequirementsRandomlyWithExcludePurpose(Purpose purpose) {
        getFullTcfCompanyEnforcementRequirements(purpose, true)
    }

    protected static String getVendorListContent(boolean includePurposes, boolean includeLegIntPurposes, boolean includeFlexiblePurposes) {
        def purposeValues = TcfConsent.PurposeId.values().value
        def vendor = VendorListResponse.Vendor.getDefaultVendor(GENERIC_VENDOR_ID).tap {
            purposes = includePurposes ? purposeValues : []
            legIntPurposes = includeLegIntPurposes ? purposeValues : []
            flexiblePurposes = includeFlexiblePurposes ? purposeValues : []
            specialPurposes = []
            features = []
            specialFeatures = []
        }
        encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = TCF_POLICY_V2.vendorListVersion
            it.vendors = [(GENERIC_VENDOR_ID): vendor]
        })
    }

    private static Purpose getRandomPurposeWithExclusion(Purpose excludeFromRandom) {
        def availablePurposes = Purpose.values().toList() - excludeFromRandom
        availablePurposes.shuffled().first()
    }
}
