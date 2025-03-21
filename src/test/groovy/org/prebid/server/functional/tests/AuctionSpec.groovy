package org.prebid.server.functional.tests

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.PrivacySandbox
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Renderer
import org.prebid.server.functional.model.request.auction.RendererData
import org.prebid.server.functional.model.request.auction.Sdk
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.Meta
import org.prebid.server.functional.model.response.auction.Prebid
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.AccountStatus.INACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.HttpUtil.COOKIE_DEPRECATION_HEADER
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AuctionSpec extends BaseSpec {

    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final boolean CORS_SUPPORT = false
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final Map<String, String> PBS_CONFIG = ["auction.biddertmax.max"    : MAX_TIMEOUT as String,
                                                           "auction.default-timeout-ms": DEFAULT_TIMEOUT as String]
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS should return version in response header for auction request for #description"() {
        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == ["pbs-java/$PBS_VERSION"]

        where:
        bidRequest                   || description
        BidRequest.defaultBidRequest || "valid bid request"
        new BidRequest()             || "invalid bid request"
    }

    def "PBS should update account.<account-id>.requests.rejected.invalid-account metric when account is inactive"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def account = new Account(uuid: accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody == "Account ${accountId} is inactive"

        and: "account.<account-id>.requests.rejected.invalid-account metric should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests.rejected.invalid-account" as String] == 1
    }

    def "PBS should update account.<account-id>.requests.rejected.#metricName metric when stored request is invalid"() {
        given: "Bid request with no stored request id"
        def noIdStoredRequest = new PrebidStoredRequest(id: null)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            updateBidRequestClosure(it, noIdStoredRequest)
        }

        and: "Initial metric count is taken"
        def accountId = bidRequest.site.publisher.id
        def fullMetricName = "account.${accountId}.requests.rejected.$metricName" as String
        def initialMetricCount = getCurrentMetricValue(defaultPbsService, fullMetricName)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request fails with an stored request id is not found error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody ==
                "Invalid request format: Stored request processing failed: Id is not found in storedRequest"

        and: "Metric count is updated"
        assert getCurrentMetricValue(defaultPbsService, fullMetricName) == initialMetricCount + 1

        where:
        metricName               | updateBidRequestClosure
        "invalid-stored-request" | { bidReq, storedReq -> bidReq.ext.prebid.storedRequest = storedReq }
        "invalid-stored-impr"    | { bidReq, storedReq -> bidReq.imp[0].ext.prebid.storedRequest = storedReq }
    }

    def "PBS should copy imp level passThrough to bidresponse.seatbid[].bid[].ext.prebid.passThrough when the passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.passThrough = passThrough
        }

        when: "Requesting PBS auction"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same passThrough as on request"
        assert response.seatbid.first().bid.first().ext.prebid.passThrough == passThrough
    }

    def "PBS should copy global level passThrough object to bidresponse.ext.prebid.passThrough when passThrough is present"() {
        given: "Default bid request with passThrough"
        def randomString = PBSUtils.randomString
        def passThrough = [(randomString): randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.passThrough = passThrough
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same passThrough as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.passThrough == passThrough
    }

    def "PBS should populate bidder request buyeruid from buyeruids when buyeruids with appropriate bidder present in request"() {
        given: "Bid request with buyeruids"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): buyeruid])))
        }

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain buyeruid from the user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid
    }

    def "PBS shouldn't populate bidder request buyeruid from buyeruids when buyeruids without appropriate bidder present in request"() {
        given: "Bid request with buyeruids"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(APPNEXUS): buyeruid])))
        }

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain buyeruid from the user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.ext
    }

    def "PBS should populate buyeruid from uids cookie when buyeruids with appropriate bidder but without value present in request"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG +
                ["adapters.${GENERIC.value}.usersync.${REDIRECT.value}.url"         : USER_SYNC_URL,
                 "adapters.${GENERIC.value}.usersync.${REDIRECT.value}.support-cors": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)


        and: "Bid request with buyeruids"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == uidsCookie.tempUIDs[GENERIC].uid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't populate buyeruid from uids cookie when buyeruids with appropriate bidder but without value present in request"() {
        given: "PBS config"
        def pbsConfig = PBS_CONFIG +
                ["adapters.${GENERIC.value}.usersync.${REDIRECT.value}.url"         : USER_SYNC_URL,
                 "adapters.${GENERIC.value}.usersync.${REDIRECT.value}.support-cors": "false"]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with buyeruids"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
        }

        and: "Empty cookies headers"
        def cookieHeader = HttpUtil.getCookieHeader(null)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request shouldn't contain buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.buyeruid

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should take precedence buyeruids whenever present valid uid cookie"() {
        given: "Bid request with buyeruids"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): buyeruid])))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain buyeruid from the buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid
    }

    def "PBS should populate buyeruid from host cookie name config when host cookie family matched with requested bidder"() {
        given: "PBS config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GENERIC_CONFIG
                + ["host-cookie.family"                          : GENERIC.value,
                   "host-cookie.cookie-name"                     : cookieName,
                   "adapters.generic.usersync.cookie-family-name": GENERIC.value])

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = HttpUtil.getCookieHeader(cookieName, hostCookieUid)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request should contain buyeruid from cookieName"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == hostCookieUid
    }

    def "PBS shouldn't populate buyeruid from cookie name config when host cookie family not matched with requested cookie-family-name"() {
        given: "PBS config"
        def cookieName = PBSUtils.randomString
        def pbsConfig = PBS_CONFIG + GENERIC_CONFIG +
                ["host-cookie.family"                          : APPNEXUS.value,
                 "host-cookie.cookie-name"                     : cookieName,
                 "adapters.generic.usersync.cookie-family-name": GENERIC.value]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = HttpUtil.getCookieHeader(cookieName, hostCookieUid)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request shouldn't contain buyeruid from cookieName"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't populate buyeruid from cookie when cookie-name in cookie and config are diferent"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GENERIC_CONFIG
                + ["host-cookie.family"                          : GENERIC.value,
                   "host-cookie.cookie-name"                     : PBSUtils.randomString,
                   "adapters.generic.usersync.cookie-family-name": GENERIC.value])

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = HttpUtil.getCookieHeader(PBSUtils.randomString, hostCookieUid)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request shouldn't contain buyeruid from cookieName"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user
    }

    def "PBS should move and not populate certain fields when debug enabled"() {
        given: "Default bid request with aliases"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.aliases = [(PBSUtils.randomString): GENERIC]
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain endpoint in ext.prebid.server.endpoint instead of ext.prebid.pbs.endpoint"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.ext?.prebid?.server?.endpoint == "/openrtb2/auction"
        assert !bidderRequest?.ext?.prebid?.pbs?.endpoint

        and: "BidderRequest shouldn't populate fields"
        assert !bidderRequest.ext.prebid.aliases
    }

    def "PBS auction should pass ext.prebid.sdk requested to bidder request when sdk specified"() {
        given: "Default bid request with aliases"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.sdk = new Sdk(renderers: [new Renderer(
                    name: PBSUtils.randomString,
                    version: PBSUtils.randomString,
                    data: new RendererData(any: PBSUtils.randomString))])
        }

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain sdk value same in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.ext.prebid.sdk.renderers.name == bidRequest.ext.prebid.sdk.renderers.name
        assert bidderRequest.ext.prebid.sdk.renderers.version == bidRequest.ext.prebid.sdk.renderers.version
        assert bidderRequest.ext.prebid.sdk.renderers.data.any == bidRequest.ext.prebid.sdk.renderers.data.any
    }

    def "PBS auction should pass meta object to bid response when meta specified "() {
        given: "Default bid request with aliases"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(
                    rendererName: PBSUtils.randomString,
                    rendererUrl: PBSUtils.randomString,
                    rendererVersion: PBSUtils.getRandomString())))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain meta value same in request"
        assert response.seatbid[0].bid[0].ext.prebid.meta.rendererName ==
                bidResponse.seatbid[0].bid[0].ext.prebid.meta.rendererName
        assert response.seatbid[0].bid[0].ext.prebid.meta.rendererUrl ==
                bidResponse.seatbid[0].bid[0].ext.prebid.meta.rendererUrl
        assert response.seatbid[0].bid[0].ext.prebid.meta.rendererVersion ==
                bidResponse.seatbid[0].bid[0].ext.prebid.meta.rendererVersion
    }

    def "PBS call to alias should populate bidder request buyeruid from family user.buyeruids when resolved name is present"() {
        given: "Pbs config with alias"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GENERIC_CONFIG
                + ["host-cookie.family"                          : GENERIC.value,
                   "host-cookie.cookie-name"                     : cookieName,
                   "adapters.generic.usersync.cookie-family-name": GENERIC.value])

        and: "Alias bid request"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            ext.prebid.aliases = [(ALIAS.value): bidderName]
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): buyeruid])))
        }

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = HttpUtil.getCookieHeader(cookieName, hostCookieUid)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request should contain buyeruid from the user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid

        where:
        bidderName << [GENERIC, GENERIC_CAMEL_CASE]
    }

    def "PBS call to alias should populate bidder request buyeruid from family user.buyeruids when it's contained in base bidder"() {
        given: "Pbs config with alias"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GENERIC_CONFIG + GENERIC_ALIAS_CONFIG
                + ["host-cookie.family"                          : GENERIC.value,
                   "host-cookie.cookie-name"                     : cookieName,
                   "adapters.generic.usersync.cookie-family-name": GENERIC.value])

        and: "Alias bid request"
        def buyeruid = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.alias = new Generic()
            imp[0].ext.prebid.bidder.generic = null
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): buyeruid])))
        }

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = HttpUtil.getCookieHeader(cookieName, hostCookieUid)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request should contain buyeruid from the user.ext.prebid.buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid

        where:
        bidderName << [GENERIC, GENERIC_CAMEL_CASE]
    }

    def "PBS should set device.ext.cdep from header when cookieDeprecation and Deprecation header is specified"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Sec-Cookie-Deprecation header"
        def secCookieDeprecation = [(COOKIE_DEPRECATION_HEADER): PBSUtils.randomString]

        when: "PBS processes auction request with header"
        defaultPbsService.sendAuctionRequest(bidRequest, secCookieDeprecation)

        then: "BidderRequest should have device.ext.cdep from sec-cookie-deprecation header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ext.cdep == secCookieDeprecation[COOKIE_DEPRECATION_HEADER]
    }

    def "PBS shouldn't set device.ext.cdep from header when cookieDeprecation config is #privacySandbox"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request with header"
        defaultPbsService.sendAuctionRequest(bidRequest, [(COOKIE_DEPRECATION_HEADER): PBSUtils.randomString])

        then: "BidderRequest shouldn't have device.ext.cdep"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.device?.ext?.cdep

        where:
        privacySandbox << [null,
                           PrivacySandbox.getDefaultPrivacySandbox(null),
                           PrivacySandbox.getDefaultPrivacySandbox(false)]
    }

    def "PBS shouldn't set device.ext.cdep when cookieDeprecation config is specified and request don't have Sec-Cookie-Deprecation header"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: PrivacySandbox.defaultPrivacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request with header"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't have device.ext.cdep"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.device?.ext?.cdep
    }

    def "PBS shouldn't update device.ext.cdep from Sec-Cookie-Deprecation header when it's present in original request"() {
        given: "BidRequest with device.ext.cdep"
        def cdep = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device(ext: new DeviceExt(cdep: cdep))
        }

        and: "Account in the DB"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Sec-Cookie-Deprecation header"
        def secCookieDeprecation = [(COOKIE_DEPRECATION_HEADER): PBSUtils.randomString]

        when: "PBS processes auction request with header"
        defaultPbsService.sendAuctionRequest(bidRequest, secCookieDeprecation)

        then: "BidderRequest should have original device.ext.cdep"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ext.cdep == cdep
    }

    def "PBS should set device.ext.cdep from header when default account contain privacy sandbox and request account is empty"() {
        given: "Pbs with default account that include privacySandbox configuration"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            auction = new AccountAuctionConfig(privacySandbox: privacySandbox)
        }
        def pbsService = pbsServiceFactory.getService(PBS_CONFIG +
                ["settings.default-account-config": encode(defaultAccountConfigSettings)])

        and: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Sec-Cookie-Deprecation header"
        def secCookieDeprecation = [(COOKIE_DEPRECATION_HEADER): PBSUtils.randomString]

        when: "PBS processes auction request with header"
        pbsService.sendAuctionRequest(bidRequest, secCookieDeprecation)

        then: "BidderRequest should have device.ext.cdep from sec-cookie-deprecation header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.ext.cdep == secCookieDeprecation[COOKIE_DEPRECATION_HEADER]
    }

    def "PBS shouldn't set device.ext.cdep from header when default account don't contain privacy sandbox and request account is empty"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Sec-Cookie-Deprecation header"
        def secCookieDeprecation = [(COOKIE_DEPRECATION_HEADER): PBSUtils.randomString]

        when: "PBS processes auction request with header"
        defaultPbsService.sendAuctionRequest(bidRequest, secCookieDeprecation)

        then: "BidderRequest shouldn't have device.ext.cdep"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.device?.ext?.cdep
    }

    def "PBS should include warning and don't set device.ext.cdep from header when Deprecation header is longer then 100 chars"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def privacySandbox = PrivacySandbox.defaultPrivacySandbox
        def accountAuctionConfig = new AccountAuctionConfig(privacySandbox: privacySandbox)
        def accountConfig = new AccountConfig(status: ACTIVE, auction: accountAuctionConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Long Sec-Cookie-Deprecation header"
        def secCookieDeprecation = [(COOKIE_DEPRECATION_HEADER): PBSUtils.getRandomString(101)]

        when: "PBS processes auction request with header"
        def response = defaultPbsService.sendAuctionRequest(bidRequest, secCookieDeprecation)

        then: "PBS should include warning in responce"
        def auctionWarnings = response.ext?.warnings?.get(PREBID)
        assert auctionWarnings.size() == 1
        assert auctionWarnings[0].code == 999
        assert auctionWarnings[0].message == 'Sec-Cookie-Deprecation header has invalid value'

        and: "BidderRequest shouldn't have device.ext.cdep"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.device?.ext?.cdep
    }
}
