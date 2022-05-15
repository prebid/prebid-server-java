package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountMetricsConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse

import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.basic
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.detailed
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.none

class MetricsSpec extends BaseSpec {

    def "PBS should not populate account metric when verbosity level is none"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: none))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* shouldn't be exists"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert !metrics.find(it -> { it.key.startsWith("account.${accountId}") })
    }

    def "PBS should update account.<account-id>.requests metric when verbosity level is basic"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def accountId = bidRequest.site.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: basic))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
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
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.adapter.generic.bids_received"     as String] == 1
        assert metrics["account.${accountId}.adapter.generic.prices"            as String] == bidPrice
        assert metrics["account.${accountId}.adapter.generic.request_time"      as String] == 1
        assert metrics["account.${accountId}.adapter.generic.requests.gotbids"  as String] == 1
        assert metrics["account.${accountId}.requests"                          as String] == 1
        assert metrics["account.${accountId}.requests.type.openrtb2-web"        as String] == 1
    }
}
