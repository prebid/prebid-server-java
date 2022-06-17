package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountMetricsConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse

import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.BASIC
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.DETAILED
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.NONE

class MetricsSpec extends BaseSpec {

    def setup() {
        flushMetrics()
    }

    def "PBS should not populate account metric when verbosity level is none"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: NONE))
        def accountId = bidRequest.site.publisher.id
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* metric shouldn't be populated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics.find { it.key.startsWith("account.${accountId}") }
    }

    def "PBS should update account.<account-id>.requests metric when verbosity level is basic"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: BASIC))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.requests should be populated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests" as String] == 1

        and: "account.<account-id>.generic and requests.type.openrtb2-web metrics shouldn't populated"
        assert !metrics.findAll({ it.key.startsWith("account.${accountId}.generic") })
        assert !metrics["account.${accountId}.requests.type.openrtb2-web" as String]
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
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: DETAILED))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* should be populated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.adapter.generic.bids_received"     as String] == 1
        assert metrics["account.${accountId}.adapter.generic.prices"            as String] == bidPrice
        assert metrics["account.${accountId}.adapter.generic.request_time"      as String] == 1
        assert metrics["account.${accountId}.adapter.generic.requests.gotbids"  as String] == 1
        assert metrics["account.${accountId}.requests"                          as String] == 1
        assert metrics["account.${accountId}.requests.type.openrtb2-web"        as String] == 1
    }
}
