package org.prebid.server.functional.tests

import org.prebid.server.functional.model.config.AccountAnalyticsConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AnalyticsModule
import org.prebid.server.functional.model.config.LogAnalytics
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.mock.services.pubstack.PubStackResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PrebidAnalytics
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.testcontainers.scaffolding.PubStackAnalytics
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Ignore
import spock.lang.Shared

class AnalyticsSpec extends BaseSpec {

    private static final String SCOPE_ID = UUID.randomUUID()
    private static final Map<String, String> ENABLED_DEBUG_LOG_MODE = ["logging.level.root": "debug"]
    private static final PrebidServerService pbsService = pbsServiceFactory.getService(PbsConfig.getPubstackAnalyticsConfig(SCOPE_ID))
    private static final PrebidServerService pbsServiceWithLogAnalytics = pbsServiceFactory.getService(
            ENABLED_DEBUG_LOG_MODE + ['analytics.log.enabled'    : 'true',
                                      'analytics.global.adapters': 'logAnalytics'])
    private static final PrebidServerService pbsServiceWithoutLogAnalytics = pbsServiceFactory.getService(
            ENABLED_DEBUG_LOG_MODE + ['analytics.log.enabled'    : 'true',
                                      'analytics.global.adapters': ''])


    @Shared
    PubStackAnalytics analytics = new PubStackAnalytics(Dependencies.networkServiceContainer).tap {
        it.setResponse(PubStackResponse.getDefaultPubStackResponse(SCOPE_ID, Dependencies.networkServiceContainer.rootUri))
    }

    @Ignore("Currently impossible to make this test pass 100% of the time")
    def "PBS should send PubStack analytics when analytics.pubstack.enabled=true"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Initial request count"
        def analyticsRequestCount = analytics.requestCount

        when: "PBS processes auction request"
        pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should call pubstack analytics"
        PBSUtils.waitUntil { analytics.requestCount == analyticsRequestCount + 1 }
    }

    def "PBS should populate log analytics when logging enabled in global config but not in account config"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: null))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS should call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert logsByValue

        and: "Analytics adapter shouldn't contain additional info"
        def analyticsBidRequest = extractResolvedRequestFromLog(logsByValue)
        assert !analyticsBidRequest?.ext?.prebid?.analytics?.logAnalytics?.additionalData
    }

    def "PBS shouldn't populate log analytics when log enabled in account and disabled in global config"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def logAnalyticsModule = new LogAnalytics(enabled: true)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithoutLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert !logsByValue
    }

    def "PBS should populate log analytics when log enabled in account and global config"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def logAnalyticsModule = new LogAnalytics(enabled: true)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS should call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert logsByValue

        and: "Analytics adapter shouldn't contain additional info"
        def analyticsBidRequest = extractResolvedRequestFromLog(logsByValue)
        assert !analyticsBidRequest?.ext?.prebid?.analytics?.logAnalytics?.additionalData
    }

    def "PBS shouldn't populate log analytics when log disabled in account and enabled in global config"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def logAnalyticsModule = new LogAnalytics(enabled: false)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert !logsByValue
    }

    def "PBS shouldn't populate log analytics when log disabled in global config and without account"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: null))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithoutLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert !logsByValue
    }

    def "PBS should populate log analytics with additional data when log enabled in account and data specified"() {
        given: "Basic bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.analytics = new PrebidAnalytics()
        }

        and: "Account in the DB"
        def additionalData = PBSUtils.randomString
        def logAnalyticsModule = new LogAnalytics(enabled: true, additionalData: additionalData)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS should call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert logsByValue

        and: "Analytics adapter should contain additional info"
        def analyticsBidRequest = extractResolvedRequestFromLog(logsByValue)
        assert analyticsBidRequest.ext.prebid.analytics.logAnalytics.additionalData == additionalData
    }

    def "PBS should populate log analytics with additional data from request when log enabled in account and data specified in request only"() {
        given: "Basic bid request"
        def additionalData = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.analytics = new PrebidAnalytics(logAnalytics: new LogAnalytics(additionalData: additionalData))
        }

        and: "Account in the DB"
        def logAnalyticsModule = new LogAnalytics(enabled: true, additionalData: null)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS should call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert logsByValue

        and: "Analytics adapter should contain additional info"
        def analyticsBidRequest = extractResolvedRequestFromLog(logsByValue)
        assert analyticsBidRequest.ext.prebid.analytics.logAnalytics.additionalData == additionalData
    }

    def "PBS should prioritize logAnalytics from request when data specified in account and request"() {
        given: "Basic bid request"
        def bidRequestAdditionalData = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.analytics = new PrebidAnalytics(logAnalytics: new LogAnalytics(additionalData: bidRequestAdditionalData))
        }

        and: "Account in the DB"
        def accountAdditionalData = PBSUtils.randomString
        def logAnalyticsModule = new LogAnalytics(enabled: true, additionalData: accountAdditionalData)
        def config = new AccountAnalyticsConfig(modules: new AnalyticsModule(logAnalytics: logAnalyticsModule))
        def accountConfig = new AccountConfig(analytics: config)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        pbsServiceWithLogAnalytics.sendAuctionRequest(bidRequest)

        then: "PBS should call log analytics"
        def logsByValue = pbsServiceWithLogAnalytics.getLogsByValue(bidRequest.id)
        assert logsByValue

        and: "Analytics adapter should contain additional info"
        def analyticsBidRequest = extractResolvedRequestFromLog(logsByValue)
        assert analyticsBidRequest.ext.prebid.analytics.logAnalytics.additionalData == bidRequestAdditionalData
    }

    private static BidRequest extractResolvedRequestFromLog(String logsByText) {
        decode(logsByText.split("resolvedrequest")[1]
                .replace(";", "")
                .replaceFirst(":", "")
                .replaceFirst("\"", ""), BidRequest.class)
    }
}
