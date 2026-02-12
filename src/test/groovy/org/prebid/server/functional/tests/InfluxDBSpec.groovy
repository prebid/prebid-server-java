package org.prebid.server.functional.tests

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerService

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED
import static org.prebid.server.functional.model.response.BidderErrorCode.BAD_INPUT
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.NoBidResponse.UNKNOWN_ERROR
import static org.prebid.server.functional.testcontainers.Dependencies.influxdbContainer

class InfluxDBSpec extends BaseSpec {

    private static final String ACCOUNT_REJECTED_METRIC = "influx.metric.account.%s.requests.rejected.invalid-account"

    private static final Map<String, String> PBS_CONFIG_WITH_INFLUX_AND_ENFORCE_VALIDATION_ACCOUNTANT = [
            "metrics.influxdb.enabled"       : "true",
            "metrics.influxdb.prefix"        : "influx.metric.",
            "metrics.influxdb.host"          : influxdbContainer.getNetworkAliases().get(0),
            "metrics.influxdb.port"          : influxdbContainer.getExposedPorts().get(0) as String,
            "metrics.influxdb.protocol"      : "http",
            "metrics.influxdb.database"      : influxdbContainer.database as String,
            "metrics.influxdb.auth"          : "${influxdbContainer.username}:${influxdbContainer.password}" as String,
            "metrics.influxdb.interval"      : "1",
            "metrics.influxdb.connectTimeout": "5000",
            "metrics.influxdb.readTimeout"   : "100",
            "settings.enforce-valid-account": true as String]

    private static final PrebidServerService pbsServiceWithEnforceValidAccount
            = pbsServiceFactory.getService(PBS_CONFIG_WITH_INFLUX_AND_ENFORCE_VALIDATION_ACCOUNTANT)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PBS_CONFIG_WITH_INFLUX_AND_ENFORCE_VALIDATION_ACCOUNTANT)
    }

    def "PBS should reject request with error and metrics when inactive account"() {
        given: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Inactive account id"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(status: AccountStatus.INACTIVE))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnforceValidAccount.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "PBS should reject the entire auction"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == ["Account $bidRequest.accountId is inactive"]
        }

        and: "PBS wait until get metric"
        assert pbsServiceWithEnforceValidAccount.isContainMetricByValue(ACCOUNT_REJECTED_METRIC.formatted(bidRequest.accountId))

        and: "PBS metrics populated correctly"
        def influxMetricsRequest = pbsServiceWithEnforceValidAccount.sendInfluxMetricsRequest()
        assert influxMetricsRequest[ACCOUNT_REJECTED_METRIC.formatted(bidRequest.accountId)] == 1
    }

    def "PBS shouldn't reject request with error and metrics when active account"() {
        given: "Default basic BidRequest with inactive account id"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Inactive account id"
        def account = new Account(uuid: bidRequest.accountId, config: new AccountConfig(status: AccountStatus.ACTIVE))
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnforceValidAccount.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seatBid"
        assert response.seatbid.size() == 1

        and: "PBs shouldn't emit metric"
        assert !pbsServiceWithEnforceValidAccount.isContainMetricByValue(ACCOUNT_REJECTED_METRIC.formatted(bidRequest.accountId))
    }
}
