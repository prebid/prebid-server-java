package org.prebid.server.functional.tests

import org.apache.commons.lang3.StringUtils
import org.prebid.server.functional.model.config.AccountAuctionConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountMetricsConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.BASIC
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.DETAILED
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.NONE
import static org.prebid.server.functional.model.request.auction.DebugCondition.DISABLED
import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.response.auction.BidderCallType.STORED_BID_RESPONSE

class DebugSpec extends BaseSpec {

    private static final String overrideToken = PBSUtils.randomString
    private static final String ACCOUNT_METRICS_PREFIX_NAME = "account"
    private static final String AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS = "debug_requests"
    private static final String ACCOUNT_REQUESTED_WITH_DEBUG_MODE_METRICS = "account.%s.debug_requests"
    private static final String REQUEST_OK_WEB_METRICS = "requests.ok.openrtb2-web"

    def "PBS should return debug information and emit metrics when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = debug
        bidRequest.test = test

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        and: "Metrics should be increase"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS] == 1

        and: "Account metrics shouldn't be populated"
        assert !metricsRequest.keySet().contains(ACCOUNT_METRICS_PREFIX_NAME)

        where:
        debug   | test
        ENABLED | null
        ENABLED | DISABLED
        null    | ENABLED
    }

    def "PBS shouldn't return debug information when debug flag is #debug and test flag is #test"() {
        given: "Default BidRequest with test flag"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = test
        bidRequest.test = test

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain ext.debug"
        assert !response.ext?.debug

        and: "Debug metrics shouldn't be populated"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS]
        assert !metricsRequest.keySet().contains(ACCOUNT_METRICS_PREFIX_NAME)

        and: "General metrics should be present"
        assert metricsRequest[REQUEST_OK_WEB_METRICS] == 1

        where:
        debug    | test
        DISABLED | null
        null     | DISABLED
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999] // [10003]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $GENERIC.value" as String]
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        //TODO possibly change message after clarifications
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for account"]

        where:
        accountConfig << [new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false)),
                          new AccountConfig(auction: new AccountAuctionConfig(debugAllowSnakeCase: false))]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = false is overridden by account-level setting debug-allowed = true"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "false"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug?.httpcalls

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10003 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["Debug turned off for bidder: $GENERIC.value" as String]

        where:
        accountConfig << [new AccountConfig(auction: new AccountAuctionConfig(debugAllow: true)),
                          new AccountConfig(auction: new AccountAuctionConfig(debugAllowSnakeCase: true))]
    }

    def "PBS should not return debug information when bidder-level setting debug.allowed = true is overridden by account-level setting debug-allowed = false"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.debug.allow": "true"])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]
    }

    def "PBS should use default values = true for bidder-level setting debug.allow and account-level setting debug-allowed when they are not specified"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return debug information when bidder-level setting debug.allowed = #debugAllowedConfig and account-level setting debug-allowed = #debugAllowedAccount is overridden by x-pbs-debug-override header"() {
        given: "PBS with debug configuration"
        def pbsService = pbsServiceFactory.getService(pbdConfig)

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: debugAllowedAccount))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": overrideToken])

        then: "Response should contain ext.debug"
        assert response.ext?.debug?.httpcalls[GENERIC.value]

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings

        where:
        debugAllowedConfig | debugAllowedAccount | pbdConfig
        false              | true                | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "false"]
        true               | false               | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "true"]
        false              | false               | ["debug.override-token"        : overrideToken,
                                                    "adapters.generic.debug.allow": "false"]
    }

    def "PBS should not return debug information when x-pbs-debug-override header is incorrect"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(["debug.override-token": overrideToken])

        and: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = ENABLED

        and: "Account in the DB"
        def accountConfig = new AccountConfig(auction: new AccountAuctionConfig(debugAllow: false))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, ["x-pbs-debug-override": headerValue])

        then: "Response should not contain ext.debug"
        assert !response.ext?.debug

        and: "Response should contain specific code and text in ext.warnings.general"
        //TODO change to 10002 after updating debug warnings
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert response.ext?.warnings[ErrorType.PREBID]?.collect { it.message } == ["Debug turned off for account"]

        where:
        headerValue << [StringUtils.swapCase(overrideToken), PBSUtils.randomString]
    }

    @PendingFeature
    def "PBS AMP should return debug information when request flag is #requestDebug and store request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug information"
        assert response.ext?.debug

        where:
        requestDebug | storedRequestDebug
        ENABLED      | DISABLED
        ENABLED      | ENABLED
        ENABLED      | null
        null         | ENABLED
    }

    def "PBS AMP shouldn't return debug information when request flag is #requestDebug and stored request flag is #storedRequestDebug"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            debug = requestDebug
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            ext.prebid.debug = storedRequestDebug
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response shouldn't contain debug information"
        assert !response.ext?.debug

        where:
        requestDebug | storedRequestDebug
        DISABLED     | ENABLED
        DISABLED     | DISABLED
        DISABLED     | null
        null         | DISABLED
        null         | null
    }

    def "PBS shouldn't populate call type when it's default bidder call"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response shouldn't contain call type"
        assert response.ext?.debug?.httpcalls[GENERIC.value].first().callType == null

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return STORED_BID_RESPONSE call type when call from stored bid response "() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain call type STORED_BID_RESPONSE"
        assert response.ext?.debug?.httpcalls[GENERIC.value].first().callType == STORED_BID_RESPONSE

        and: "Response should not contain ext.warnings"
        assert !response.ext?.warnings
    }

    def "PBS should return debug information and emit metrics when account debug enabled and verbosity detailed"() {
        given: "Default basic generic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                metrics: new AccountMetricsConfig(verbosityLevel: DETAILED),
                auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        and: "Metrics account should be incremented"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[ACCOUNT_REQUESTED_WITH_DEBUG_MODE_METRICS.formatted(bidRequest.accountId)] == 1
        assert metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS] == 1
    }

    def "PBS shouldn't return debug information and emit metrics when account debug enabled and verbosity #verbosityLevel"() {
        given: "Default basic generic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                metrics: new AccountMetricsConfig(verbosityLevel: verbosityLevel),
                auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        and: "Metrics shouldn't be incremented"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest[ACCOUNT_REQUESTED_WITH_DEBUG_MODE_METRICS.formatted(bidRequest.accountId)]

        and: "Metrics should be incremented"
        assert metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS]

        where:
        verbosityLevel << [NONE, BASIC]
    }

    def "PBS amp should return debug information and emit metrics when account debug enabled and verbosity detailed"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                metrics: new AccountMetricsConfig(verbosityLevel: DETAILED),
                auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        and: "Metrics account should be incremented"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert metricsRequest[ACCOUNT_REQUESTED_WITH_DEBUG_MODE_METRICS.formatted(ampRequest.account)] == 1
        assert metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS] == 1
    }

    def "PBS amp should return debug information and emit metrics when account debug enabled and verbosity #verbosityLevel"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Account in the DB"
        def accountConfig = new AccountConfig(
                metrics: new AccountMetricsConfig(verbosityLevel: verbosityLevel),
                auction: new AccountAuctionConfig(debugAllow: true))
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain ext.debug"
        assert response.ext?.debug

        and: "Metrics shouldn't be incremented"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest[ACCOUNT_REQUESTED_WITH_DEBUG_MODE_METRICS.formatted(ampRequest.account)]

        and: "Metrics should be incremented"
        assert metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS] == 1

        where:
        verbosityLevel << [NONE, BASIC]
    }

    def "PBS shouldn't emit auction request metric when incoming request invalid"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = ENABLED

        and: "Flash metrics"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("request.site should include at least one of request.site.id or request.site.page")

        and: "Debug metrics shouldn't be populated"
        def metricsRequest = defaultPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest[AUCTION_REQUESTED_WITH_DEBUG_MODE_METRICS]
        assert !metricsRequest.keySet().contains(ACCOUNT_METRICS_PREFIX_NAME)

        and: "General metrics shouldn't be present"
        assert !metricsRequest[REQUEST_OK_WEB_METRICS]
    }
}
