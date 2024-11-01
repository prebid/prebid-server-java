package org.prebid.server.functional.tests.module.pbrequestcorrection

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.PbRequestCorrectionConfig
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppPrebid
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.OperationState
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.OperationState.YES

class PbRequestCorrectionSpec extends ModuleBaseSpec {

    private static final String PREBID_MOBILE = "prebid-mobile"
    private static final String DEVICE_PREBID_MOBILE_PATTERN = "PrebidMobile/"
    private static final String ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION = PBSUtils.getRandomVersion("0.0", "2.1.5")
    private static final String ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION = PBSUtils.getRandomVersion("0.0", "2.2.3")
    private static final String ANDROID = "android"
    private static final String IOS = "IOS"

    private PrebidServerService pbsServiceWithRequestCorrectionModule = pbsServiceFactory.getService(requestCorrectionSettings)

    def "PBS should remove positive instl from imps for android app when request correction is enabled for account"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp = imps
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl.every { it == null }

        where:
        imps                                                                                    | bundle
        [Imp.defaultImpression.tap { instl = YES }]                                             | "$ANDROID${PBSUtils.randomString}"
        [Imp.defaultImpression.tap { instl = null }, Imp.defaultImpression.tap { instl = YES }] | "${PBSUtils.randomString}$ANDROID${PBSUtils.randomString}"
        [Imp.defaultImpression.tap { instl = YES }, Imp.defaultImpression.tap { instl = null }] | "${PBSUtils.randomString}$ANDROID${PBSUtils.getRandomNumber()}"
        [Imp.defaultImpression.tap { instl = YES }, Imp.defaultImpression.tap { instl = YES }]  | "$ANDROID${PBSUtils.randomString}_$ANDROID${PBSUtils.getRandomNumber()}"
    }

    def "PBS shouldn't remove negative instl from imps for android app when request correction is enabled for account"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp = imps
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl

        where:
        imps << [[Imp.defaultImpression.tap { instl = OperationState.NO }],
                 [Imp.defaultImpression.tap { instl = null }, Imp.defaultImpression.tap { instl = OperationState.NO }],
                 [Imp.defaultImpression.tap { instl = OperationState.NO }, Imp.defaultImpression.tap { instl = null }],
                 [Imp.defaultImpression.tap { instl = OperationState.NO }, Imp.defaultImpression.tap { instl = OperationState.NO }]]
    }

    def "PBS shouldn't remove positive instl from imps for not android or not prebid-mobile app when request correction is enabled for account"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(source), version: PBSUtils.getRandomVersion("0.0", ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION))
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = YES
            app.bundle = PBSUtils.getRandomCase(bundle)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl

        where:
        bundle                | source
        IOS                   | PREBID_MOBILE
        PBSUtils.randomString | PREBID_MOBILE
        ANDROID               | PBSUtils.randomString
        ANDROID               | PBSUtils.randomString + PREBID_MOBILE
        ANDROID               | PREBID_MOBILE + PBSUtils.randomString
        ANDROID               | PREBID_MOBILE + PBSUtils.randomString
    }

    def "PBS shouldn't remove positive instl from imps for app when request correction is enabled for account but some required parameter is empty"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: source, version: version)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = instl
            app.bundle = bundle
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl

        where:
        bundle  | source        | version                                       | instl
        null    | PREBID_MOBILE | ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION | YES
        ANDROID | null          | ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION | YES
        ANDROID | PREBID_MOBILE | null                                          | YES
        ANDROID | PREBID_MOBILE | ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION | null
    }

    def "PBS shouldn't remove positive instl from imps for android app when request correction is enabled for account and version is 2.2.3"() {
        given: "Android APP bid request with version equal to 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: "2.2.3")
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = YES
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl
    }

    def "PBS shouldn't remove positive instl from imps for android app when request correction is enabled for account and version is higher then 2.2.3"() {
        given: "Android APP bid request with version higher then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: PBSUtils.getRandomVersion("2.2.4"))
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = YES
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithInterstitial
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl
    }

    def "PBS shouldn't remove positive instl from imps for android app when request correction is disabled for account"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = YES
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.getDefaultConfigWithInterstitial(interstitialCorrectionEnabled, enabled)
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl

        where:
        enabled | interstitialCorrectionEnabled
        false   | true
        null    | true
        true    | false
        true    | null
        null    | null
    }

    def "PBS shouldn't remove positive instl from imps for android app when request correction is not applied for account"() {
        given: "Android APP bid request with version lover then 2.2.3"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: ACCEPTABLE_DEVICE_INSTL_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            imp.first.instl = YES
            app.bundle = PBSUtils.getRandomCase(ANDROID)
            app.ext = new AppExt(prebid: prebid)
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: new PbsModulesConfig()))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain original imp.instl"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.instl == bidRequest.imp.instl
    }

    def "PBS should remove pattern device.ua when request correction is enabled for account and user agent correction enabled"() {
        given: "Android APP bid request with version lover then 2.1.5"
        def prebid = new AppPrebid(source: PREBID_MOBILE, version: ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: prebid)
            device = new Device(ua: deviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.device.ua

        where:
        deviceUa << ["${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}",
                     "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}${PBSUtils.randomString}"]
    }

    def "PBS should remove only pattern device.ua when request correction is enabled for account and user agent correction enabled"() {
        given: "Android APP bid request with version lover then 2.1.5"
        def prebid = new AppPrebid(source: PREBID_MOBILE, version: ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: prebid)
            device = new Device(ua: deviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua

        where:
        deviceUa << ["${PBSUtils.randomNumber} ${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber} ${PBSUtils.randomString}",
                     "${PBSUtils.randomString} ${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}${PBSUtils.randomString} ${PBSUtils.randomString}",
                     "${DEVICE_PREBID_MOBILE_PATTERN}",
                     "${DEVICE_PREBID_MOBILE_PATTERN} ${PBSUtils.randomNumber}",
                     "${DEVICE_PREBID_MOBILE_PATTERN} ${PBSUtils.randomNumber} ${PBSUtils.randomString}"

        ]
    }

    def "PBS shouldn't remove pattern device.ua when request correction is enabled for account and user agent correction disabled"() {
        given: "Android APP bid request with version lover then 2.1.5"
        def deviceUserAgent = "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}"
        def prebid = new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION)
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: prebid)
            device = new Device(ua: deviceUserAgent)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.getDefaultConfigWithUserAgentCorrection(userAgentCorrectionEnabled, enabled)
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == deviceUserAgent

        where:
        enabled | userAgentCorrectionEnabled
        false   | true
        null    | true
        true    | false
        true    | null
        null    | null
    }

    def "PBS shouldn't remove pattern device.ua when request correction is enabled for account and source not a prebid-mobile"() {
        given: "Android APP bid request with version lover then 2.1.5"
        def randomDeviceUa = "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: new AppPrebid(source: source, version: ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION))
            device = new Device(ua: randomDeviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == randomDeviceUa

        where:
        source << ["prebid",
                   "mobile",
                   "mobile-prebid" + PBSUtils.randomString,
                   PBSUtils.randomString + "mobile-prebid",
                   "mobile-prebid",
                   PBSUtils.randomString]
    }

    def "PBS shouldn't remove pattern device.ua when request correction is enabled for account and version biggest that 2.1.6"() {
        given: "Android APP bid request with version biggest then 2.1.6"
        def randomDeviceUa = "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: PBSUtils.getRandomVersion("2.1.6")))
            device = new Device(ua: randomDeviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == randomDeviceUa
    }

    def "PBS shouldn't remove pattern device.ua when request correction is enabled for account and version 2.1.6"() {
        given: "Android APP bid request with version 2.1.6"
        def randomDeviceUa = "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: "2.1.6"))
            device = new Device(ua: randomDeviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == randomDeviceUa
    }

    def "PBS shouldn't remove device.ua pattern when request correction is enabled for account and version biggest that 2.1.6"() {
        given: "Android APP bid request with version biggest then 2.1.6"
        def randomDeviceUa = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: new AppPrebid(source: PBSUtils.getRandomCase(PREBID_MOBILE), version: PBSUtils.getRandomVersion("2.1.6")))
            device = new Device(ua: randomDeviceUa)
        }

        and: "Account in the DB"
        def requestCorrectionConfig = PbRequestCorrectionConfig.defaultConfigWithUserAgentCorrection
        def account = createAccountWithRequestCorrectionConfig(bidRequest, requestCorrectionConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain device.ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == randomDeviceUa
    }

    def "PBS shouldn't remove device.ua pattern from device for android app when request correction is not applied for account"() {
        given: "Android APP bid request with version lover then 2.1.5"
        def prebid = new AppPrebid(source: PREBID_MOBILE, version: ACCEPTABLE_DEVICE_UA_PREBID_MOBILE_VERSION)
        def deviceUa = "${DEVICE_PREBID_MOBILE_PATTERN}${PBSUtils.randomNumber}"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.ext = new AppExt(prebid: prebid)
            device = new Device(ua: deviceUa)
        }

        and: "Account in the DB"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: new PbsModulesConfig()))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithRequestCorrectionModule.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain request device ua"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ua == deviceUa
    }

    private static Account createAccountWithRequestCorrectionConfig(BidRequest bidRequest,
                                                                    PbRequestCorrectionConfig requestCorrectionConfig) {
        def pbsModulesConfig = new PbsModulesConfig(pbRequestCorrection: requestCorrectionConfig)
        def accountHooksConfig = new AccountHooksConfiguration(modules: pbsModulesConfig)
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        new Account(uuid: bidRequest.accountId, config: accountConfig)
    }
}
