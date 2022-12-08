package org.prebid.server.functional.tests

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature
import spock.lang.Shared

import static org.prebid.server.functional.model.AccountStatus.INACTIVE
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AuctionSpec extends BaseSpec {

    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final Map<String, String> PBS_CONFIG = ["auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                                                           "auction.default-timeout-ms": DEFAULT_TIMEOUT as String]
    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS should return version in response header for auction request for #description"() {

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == "pbs-java/$PBS_VERSION"

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
        def initialMetricCount = getCurrentMetricValue(fullMetricName)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request fails with an stored request id is not found error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody ==
                "Invalid request format: Stored request processing failed: Id is not found in storedRequest"

        and: "Metric count is updated"
        assert getCurrentMetricValue(fullMetricName) == initialMetricCount + 1

        where:
        metricName               | updateBidRequestClosure
        "invalid-stored-request" | { bidReq, storedReq -> bidReq.ext.prebid.storedRequest = storedReq }
        "invalid-stored-impr"    | { bidReq, storedReq -> bidReq.imp[0].ext.prebid.storedRequest = storedReq }
    }

    def "PBS should generate UUID for APP BidRequest id and merge StoredRequest when generate-storedrequest-bidrequest-id = #generateBidRequestId"() {
        given: "PBS config with settings.generate-storedrequest-bidrequest-id and default-account-config"
        def pbsService = pbsServiceFactory.getService(["settings.generate-storedrequest-bidrequest-id": (generateBidRequestId)])

        and: "Flush metrics"
        flushMetrics(pbsService)

        and: "Default bid request with stored request and id"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            id = bidRequestId
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Save storedRequest into DB with cur and id"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(id: "stored-request-id", cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        def bidResponse = pbsService.sendAuctionRequest(bidRequest)

        then: "Metric stored_requests_found should be updated"
        def metrics = pbsService.sendCollectedMetricsRequest()
        assert metrics["stored_requests_found"] == 1

        and: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidResponse.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "Actual bid request ID should be different from incoming bid request id"
        assert bidderRequest.id != bidRequestId

        where:
        bidRequestId             |  generateBidRequestId
        "{{UUID}}"               |  "false"
        PBSUtils.randomString    |  "true"
    }

    def "PBS shouldn't generate UUID for BidRequest id when BidRequest doesn't have APP"() {
        given: "Default bid request with stored request and id"
        def bidRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            id = bidRequestId
            ext.prebid.storedRequest =  new PrebidStoredRequest(id:  PBSUtils.randomNumber)
        }

        and: "Save storedRequest into DB with cur and id"
        def currencies = [Currency.BOGUS]
        def storedBidRequest = new BidRequest(id: "stored-request-id", cur: currencies)
        def storedRequest = StoredRequest.getStoredRequest(bidRequest, storedBidRequest)
        storedRequestDao.save(storedRequest)

        when: "Requesting PBS auction"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidResponse should be merged with stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.cur.first() == currencies[0]

        and: "BidderRequest and bidRequest ids should be equal"
        assert bidderRequest.id == bidRequestId
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

        then: "Bidder request should contain buyeruid from the buyeruids"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == buyeruid
    }

    def "PBS should populate buyeruid from uids cookie when buyeruids with appropriate bidder but without value present in request"() {
        given: "PBS config"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["adapters.${GENERIC.value}.usersync.${REDIRECT.value}.url"         : USER_SYNC_URL,
                   "adapters.${GENERIC.value}.usersync.${REDIRECT.value}.support-cors": "false"])

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
    }

    def "PBS should take precedence buyeruids when "() {
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

    @PendingFeature
    def "PBS should populate buyeruid from host cookie name config when host cookie family matched with requested bidder"() {
        given: "PBS config"
        def cookieName = PBSUtils.randomString
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG
                + ["host-cookie.family"     : GENERIC.value,
                   "host-cookie.cookie-name": cookieName])

        and: "Bid request with buyeruids"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Host cookie"
        def hostCookieUid = UUID.randomUUID().toString()
        def cookies = [(cookieName): hostCookieUid]

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookies)

        then: "Bidder request should contain buyeruid from cookieName"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == hostCookieUid
    }
}
