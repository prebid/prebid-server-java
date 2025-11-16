package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED

class InfluxDBSpec extends BaseSpec {

    def "PBS should reject request with error and metrics when inactive account"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account": enforceValidAccount as String])

        and: "Inactive account id"
        def accountId = PBSUtils.randomNumber
        def account = new Account(uuid: accountId, config: new AccountConfig(status: AccountStatus.INACTIVE))
        accountDao.save(account)

        and: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should reject the entire auction"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == UNAUTHORIZED.code()
        assert exception.responseBody == "Account $accountId is inactive"

        and: "PBs should emit proper metric"
        PBSUtils.waitUntil({
            def metricsRequest = pbsService.sendInfluxMetricsRequest()
            metricsRequest["influx.metric.account.${bidRequest.accountId}.requests.rejected.invalid-account"] != null
        })

        where:
        enforceValidAccount << [true, false]
    }

    def "PBS shouldn't reject request with error and metrics when active account"() {
        given: "Pbs config with enforce-valid-account and default-account-config"
        def pbsService = pbsServiceFactory.getService(
                ["settings.enforce-valid-account": enforceValidAccount as String])

        and: "Inactive account id"
        def accountId = PBSUtils.randomNumber
        def account = new Account(uuid: accountId, config: new AccountConfig(status: AccountStatus.ACTIVE))
        accountDao.save(account)

        and: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = accountId
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "PBs should emit proper metric"
        PBSUtils.waitUntil({
            def metricsRequest = pbsService.sendInfluxMetricsRequest()
            metricsRequest["influx.metric.account.${bidRequest.accountId}.requests.rejected.invalid-account"] == null
        })

        where:
        enforceValidAccount << [true, false]
    }
}
