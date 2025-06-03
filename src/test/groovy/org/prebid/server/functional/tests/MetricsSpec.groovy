package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountMetricsConfig
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Dooh
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService

import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.BASIC
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.DETAILED
import static org.prebid.server.functional.model.config.AccountMetricsVerbosityLevel.NONE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH

class MetricsSpec extends BaseSpec {

    private static final PrebidServerService softPrebidService = pbsServiceFactory.getService(['auction.strict-app-site-dooh': 'false'])

    def setup() {
        flushMetrics(defaultPbsService)
        flushMetrics(softPrebidService)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(['auction.strict-app-site-dooh': 'false'])
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

        where:
        accountMetricsConfig << [new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: BASIC)),
                                 new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevelSnakeCase: BASIC))]
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

    def "PBS should update hood metrics when bid request contains hood channel type and verbosity level is detailed"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.getDefaultBidRequest(DOOH)

        and: "Account in the DB"
        def accountId = bidRequest.dooh.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: DETAILED))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "account.<account-id>.* should be populated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests.type.openrtb2-dooh" as String] == 1
        assert metrics["adapter.generic.requests.type.openrtb2-dooh" as String] == 1

        and: "ather channel types should not be populated"
        assert !metrics["account.${accountId}.requests.type.openrtb2-web" as String]
        assert !metrics["account.${accountId}.requests.type.openrtb2-app" as String]
    }

    def "PBS with soft setup should ignore site distribution channel and update only dooh metrics when presented dooh and site in request"() {
        given: "Default bid request with dooh and site"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            dooh = Dooh.defaultDooh
        }

        and: "Account in the DB"
        def accountId = bidRequest.getAccountId()
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: DETAILED))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "Requesting PBS auction"
        softPrebidService.sendAuctionRequest(bidRequest)

        then: "Bidder request should have only dooh data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.dooh
        assert !bidderRequest.site

        and: "Metrics processed across site should be updated"
        def metrics = softPrebidService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests.type.openrtb2-dooh" as String] == 1
        assert metrics["adapter.generic.requests.type.openrtb2-dooh" as String] == 1

        and: "alert.general metric should be updated"
        assert metrics[ALERT_GENERAL] == 1

        and: "Other channel types should not be populated"
        assert !metrics["account.${accountId}.requests.type.openrtb2-web" as String]
        assert !metrics["account.${accountId}.requests.type.openrtb2-app" as String]
    }

    def "PBS with soft setup should ignore other distribution channel and update only app metrics when presented app ant other channels in request"() {
        given: "Account in the DB"
        def accountId = bidRequest.app.publisher.id
        def accountMetricsConfig = new AccountConfig(metrics: new AccountMetricsConfig(verbosityLevel: DETAILED))
        def account = new Account(uuid: accountId, config: accountMetricsConfig)
        accountDao.save(account)

        when: "Requesting PBS auction"
        softPrebidService.sendAuctionRequest(bidRequest)

        then: "Bidder request should have only site data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.app
        assert !bidderRequest.site
        assert !bidderRequest.dooh

        and: "Metrics processed across site should be updated"
        def metrics = softPrebidService.sendCollectedMetricsRequest()
        assert metrics["account.${accountId}.requests.type.openrtb2-app" as String] == 1
        assert metrics["adapter.generic.requests.type.openrtb2-app" as String] == 1

        and: "alert.general metric should be updated"
        assert metrics[ALERT_GENERAL] == 1

        and: "Other channel types should not be populated"
        assert !metrics["account.${accountId}.requests.type.openrtb2-dooh" as String]
        assert !metrics["account.${accountId}.requests.type.openrtb2-web" as String]

        where:
        bidRequest << [BidRequest.getDefaultBidRequest(APP).tap {
                           it.dooh = Dooh.defaultDooh
                       },
                       BidRequest.getDefaultBidRequest(APP).tap {
                           it.site = Site.defaultSite
                       },
                       BidRequest.getDefaultBidRequest(APP).tap {
                           it.site = Site.defaultSite
                           it.dooh = Dooh.defaultDooh
                       }]
    }
}
