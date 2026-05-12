package org.prebid.server.functional.tests.module.optabletargeting

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.IdentifierType
import org.prebid.server.functional.model.config.OperatingSystem
import org.prebid.server.functional.model.config.OptableTargetingConfig
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.PublicCountryIp
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.StoredCache
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.Metrics
import org.prebid.server.functional.util.PBSUtils

import static org.apache.http.HttpStatus.SC_NOT_FOUND
import static org.prebid.server.functional.model.config.ModuleName.OPTABLE_TARGETING
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class CacheStorageSpec extends ModuleBaseSpec {

    private static final StoredCache storedCache = new StoredCache(networkServiceContainer)

    private static final Map<String, String> CACHE_STORAGE_CONFIG = ['storage.pbc.path'           : 'stored-cache',
                                                                     'storage.pbc.call-timeout-ms': '1000',
                                                                     'storage.pbc.enabled'        : 'true',
                                                                     'cache.module.enabled'       : 'true',
                                                                     'pbc.api.key'                : PBSUtils.randomString,
                                                                     'cache.api-key-secured'      : 'false']
    private static final Map<String, String> MODULE_STORAGE_CACHE_CONFIG = getOptableTargetingSettings() + CACHE_STORAGE_CONFIG
    private static final PrebidServerService prebidServerStoredCacheService = pbsServiceFactory.getService(MODULE_STORAGE_CACHE_CONFIG)

    def setup() {
        storedCache.reset()
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(MODULE_STORAGE_CACHE_CONFIG)
    }

    def "PBS should update error metrics when no cached requests present"() {
        given: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def system = PBSUtils.getRandomEnum(OperatingSystem)
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa, system)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(IdentifierType.fromOS(system)): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for new saved text storage cache"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.readErr(OPTABLE_TARGETING)] == 1

        and: "No updates for success metrics"
        assert !metrics[Metrics.Cache.creativeSizeText(OPTABLE_TARGETING)]
        assert !metrics[Metrics.Cache.creativeTtlText(OPTABLE_TARGETING)]
        assert !metrics[Metrics.Cache.readOk(OPTABLE_TARGETING)]
    }

    def "PBS should update error metrics when external service responded with invalid values"() {
        given: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def system = PBSUtils.getRandomEnum(OperatingSystem)
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa, system)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(IdentifierType.fromOS(system)): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Mocked external request"
        storedCache.setTargetingResponse(bidRequest, targetingConfig)
        storedCache.setCachingResponse(SC_NOT_FOUND)

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update error metrics"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.writeErr(OPTABLE_TARGETING)] == 1

        and: "No updates for success metrics"
        assert !metrics[Metrics.Cache.writeOk(OPTABLE_TARGETING)]
    }

    def "PBS should update metrics for new saved text storage cache when no cached requests"() {
        given: "Current value of metric prebid cache"
        def okInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.writeOk(OPTABLE_TARGETING))

        and: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def system = PBSUtils.getRandomEnum(OperatingSystem)
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa, system)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(IdentifierType.fromOS(system)): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Mocked external request"
        def targetingResult = storedCache.setTargetingResponse(bidRequest, targetingConfig)
        storedCache.setCachingResponse()

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for new saved text storage cache"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.creativeSizeText(OPTABLE_TARGETING)] != 0
        assert metrics[Metrics.Cache.writeOk(OPTABLE_TARGETING)] == okInitialValue + 1

        and: "PBS should include histogram metric"
        assert metrics[Metrics.Cache.creativeTtlText(OPTABLE_TARGETING)]
    }

    def "PBS should update metrics for stored cached requests cache when proper record present"() {
        given: "Current value of metric prebid cache"
        def textInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.creativeSizeText(OPTABLE_TARGETING))
        def ttlInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.creativeTtlText(OPTABLE_TARGETING))
        def writeInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.writeOk(OPTABLE_TARGETING))
        def readErrorInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.readErr(OPTABLE_TARGETING))
        def writeErrorInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, Metrics.Cache.writeErr(OPTABLE_TARGETING))

        and: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def system = PBSUtils.getRandomEnum(OperatingSystem)
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa, system)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(IdentifierType.fromOS(system)): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Mocked external request"
        storedCache.setCachedTargetingResponse(bidRequest)
        storedCache.setCachingResponse()

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for stored cached requests"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.readOk(OPTABLE_TARGETING)] == 1

        and: "No updates for new saved text storage metrics"
        assert metrics[Metrics.Cache.creativeSizeText(OPTABLE_TARGETING)] == textInitialValue
        assert metrics[Metrics.Cache.creativeTtlText(OPTABLE_TARGETING)] == ttlInitialValue
        assert metrics[Metrics.Cache.writeOk(OPTABLE_TARGETING)] == writeInitialValue

        and: "No update for error metrics"
        assert metrics[Metrics.Cache.readErr(OPTABLE_TARGETING)] == readErrorInitialValue
        assert metrics[Metrics.Cache.writeErr(OPTABLE_TARGETING)] == writeErrorInitialValue
    }

    private static BidRequest getBidRequestForModuleCacheStorage(String ifa, OperatingSystem os) {
        BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.user = new User(id: PBSUtils.randomString, data: [Data.defaultData], eids: [Eid.defaultEid])
            it.device = new Device(geo: Geo.FPDGeo,
                    ip: PBSUtils.getRandomEnum(PublicCountryIp.class).v4,
                    ifa: ifa,
                    ua: PBSUtils.randomString,
                    os: os)
        }
    }

    private static Account createAccountWithRequestCorrectionConfig(BidRequest bidRequest,
                                                                    OptableTargetingConfig optableTargetingConfig) {

        def pbsModulesConfig = new PbsModulesConfig(optableTargeting: optableTargetingConfig)
        def accountHooksConfig = new AccountHooksConfiguration(modules: pbsModulesConfig)
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        new Account(uuid: bidRequest.accountId, config: accountConfig)
    }
}
