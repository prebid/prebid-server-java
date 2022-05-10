package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountMetricsConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.utility.MountableFile
import spock.lang.Shared

import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.*
import static org.prebid.server.functional.testcontainers.container.PrebidServerContainer.APP_WORKDIR
import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AuctionSpec extends BaseSpec {

    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final String PBS_VERSION_HEADER = "pbs-java/$PBS_VERSION"

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                                                                            "auction.default-timeout-ms": DEFAULT_TIMEOUT as String])

    def "PBS should return version in response header for auction request for #description"() {

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == PBS_VERSION_HEADER

        where:
        bidRequest                   || description
        BidRequest.defaultBidRequest || "valid bid request"
        new BidRequest()             || "invalid bid request"
    }

    def "PBS should apply timeout from stored request when it's not specified in the auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def timeout = getRandomTimeout()
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == timeout as Long
    }

    def "PBS should prefer timeout from the auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def timeout = getRandomTimeout()
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = timeout
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            it.tmax = tmaxStoredRequest
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == timeout as Long

        where:
        tmaxStoredRequest << [null, getRandomTimeout()]
    }

    def "PBS should honor max timeout from the settings for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = auctionRequestTimeout
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getDbStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == MAX_TIMEOUT as Long

        where:
        auctionRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1       || null
        null                  || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1       || MAX_TIMEOUT + 1
    }

    def "PBS should honor default timeout for auction request"() {
        given: "Default basic BidRequest without timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = null
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request without timeout"
        def storedRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequestModel = StoredRequest.getDbStoredRequest(bidRequest, storedRequest)
        storedRequestDao.save(storedRequestModel)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == DEFAULT_TIMEOUT as Long
    }

    def "PBS should take data by priority when request, stored request, default request are defined"() {
        given: "Default request with timeout"
        def defaultRequestModel = new BidRequest(tmax: defaultRequestTmax)
        def defaultRequest = PBSUtils.createJsonFile(defaultRequestModel)

        and: "Pbs config with default request"
        def pbsContainer = new PrebidServerContainer(
                ["default-request.file.path" : APP_WORKDIR + defaultRequest.fileName,
                 "auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                 "auction.default-timeout-ms": DEFAULT_TIMEOUT as String]).tap {
            withCopyFileToContainer(MountableFile.forHostPath(defaultRequest), APP_WORKDIR) }
        pbsContainer.start()
        def pbsService = new PrebidServerService(pbsContainer, mapper)

        and: "Default basic BidRequest with timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            tmax = requestTmax
            ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomNumber)
        }

        and: "Default stored request with timeout"
        def storedRequestModel = BidRequest.defaultStoredRequest.tap {
            tmax = storedRequestTmax as Long
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(bidRequest, storedRequestModel)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        then: "Bidder request should contain correct tmax"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.tmax == DEFAULT_TIMEOUT as Long

        cleanup: "Stop container with default request"
        pbsContainer.stop()

        where:
        requestTmax     | storedRequestTmax  | defaultRequestTmax
        DEFAULT_TIMEOUT | getRandomTimeout() | getRandomTimeout()
        null            | DEFAULT_TIMEOUT    | getRandomTimeout()
        null            | null               | DEFAULT_TIMEOUT
    }

    def "PBS should not populate account metric when verbosity level is none"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: none))
        def account = new Account(uuid: accountId,config: accountMetricsConfig )
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* shouldn't be exists"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics["account.${accountId}.adapter.generic.bids_received"    as String]
        assert !metrics["account.${accountId}.adapter.generic.prices"           as String]
        assert !metrics["account.${accountId}.adapter.generic.request_time"     as String]
        assert !metrics["account.${accountId}.adapter.generic.requests.gotbids" as String]
        assert !metrics["account.${accountId}.requests"                         as String]
        assert !metrics["account.${accountId}.requests.type.openrtb2-web"       as String]
    }

    def "PBS should update account.<account-id>.requests metric when verbosity level is basic"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: basic))
        def account = new Account(uuid: accountId,config: accountMetricsConfig )
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.requests should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests" as String] == 1
    }

    def "PBS should update account.<account-id>.* metrics when verbosity level is detailed"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default basic BidResponse with bid price"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bidPrice = bidResponse.seatbid.first().bid.first().price * 1000

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: detailed))
        def account = new Account(uuid: accountId,config: accountMetricsConfig )
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.adapter.generic.bids_received"    as String] == 1
        assert metrics["account.${accountId}.adapter.generic.prices"           as String] == bidPrice
        assert metrics["account.${accountId}.adapter.generic.request_time"     as String] == 1
        assert metrics["account.${accountId}.adapter.generic.requests.gotbids" as String] == 1
        assert metrics["account.${accountId}.requests"                         as String] == 1
        assert metrics["account.${accountId}.requests.type.openrtb2-web"       as String] == 1
    }
}
