package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED

class InfluxDBSpec extends BaseSpec {

    private static final PrebidServerService pbsServiceWithEnforceValidAccount
            = pbsServiceFactory.getService(["settings.enforce-valid-account": true as String])
    private static final Closure<String> REJECT_INVALID_ACCOUNT_METRIC = { accountId ->
        "influx.metric.account.${accountId}.requests.rejected.invalid-account"
    }

    def "PBS should reject request with error and metrics when inactive account"() {
        given: "Inactive account id"
        def accountId = PBSUtils.randomNumber
        def account = new Account(uuid: accountId, config: new AccountConfig(status: AccountStatus.INACTIVE))
        accountDao.save(account)

        and: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        pbsServiceWithEnforceValidAccount.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Account $accountId is inactive"

        and: "PBs should emit proper metric"
        PBSUtils.waitUntil({
            pbsServiceWithEnforceValidAccount.sendInfluxMetricsRequest()
                    .containsKey(REJECT_INVALID_ACCOUNT_METRIC(bidRequest.accountId) as String)
        })
        def influxMetricsRequest = pbsServiceWithEnforceValidAccount.sendInfluxMetricsRequest()
        assert influxMetricsRequest[REJECT_INVALID_ACCOUNT_METRIC(bidRequest.accountId) as String] == 1
    }

    def "PBS shouldn't reject request with error and metrics when active account"() {
        given: "Inactive account id"
        def accountId = PBSUtils.randomNumber
        def account = new Account(uuid: accountId, config: new AccountConfig(status: AccountStatus.ACTIVE))
        accountDao.save(account)

        and: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        def response = pbsServiceWithEnforceValidAccount.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "PBs shouldn't emit metric"
        PBSUtils.waitUntil({
            !pbsServiceWithEnforceValidAccount.sendInfluxMetricsRequest()
                    .containsKey(REJECT_INVALID_ACCOUNT_METRIC(bidRequest.accountId) as String)
        })
    }
}
