package org.prebid.server.functional.tests.module.optabletargeting

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.OptableTargetingConfig
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.scaffolding.StoredCache
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.apache.commons.codec.binary.Base64.encodeBase64
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404
import static org.prebid.server.functional.model.ModuleName.OPTABLE_TARGETING
import static org.prebid.server.functional.model.config.IdentifierType.GOOGLE_GAID
import static org.prebid.server.functional.model.config.OperatingSystem.ANDROID
import static org.prebid.server.functional.model.request.auction.PublicCountryIp.USA_IP
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class CacheStorageSpec extends ModuleBaseSpec {

    private static final String METRIC_CREATIVE_SIZE_TEXT = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.entry_size.text"
    private static final String METRIC_CREATIVE_TTL_TEXT = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.entry_ttl.text"

    private static final String METRIC_CREATIVE_READ_OK = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.read.ok"
    private static final String METRIC_CREATIVE_READ_ERR = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.read.err"
    private static final String METRIC_CREATIVE_WRITE_OK = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.write.ok"
    private static final String METRIC_CREATIVE_WRITE_ERR = "prebid_cache.module_storage.${OPTABLE_TARGETING.code}.write.err"

    private static final StoredCache storedCache = new StoredCache(networkServiceContainer)

    private static final Map<String, String> CACHE_STORAGE_CONFIG = ['storage.pbc.path'           : "$networkServiceContainer.rootUri/stored-cache".toString(),
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
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(GOOGLE_GAID): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for new saved text storage cache"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[METRIC_CREATIVE_READ_ERR] == 1

        and: "No updates for success metrics"
        assert !metrics[METRIC_CREATIVE_SIZE_TEXT]
        assert !metrics[METRIC_CREATIVE_TTL_TEXT]
        assert !metrics[METRIC_CREATIVE_READ_OK]
    }

    def "PBS should update error metrics when external service responded with invalid values"() {
        given: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(GOOGLE_GAID): randomIfa])
        def account = createAccountWithRequestCorrectionConfig(bidRequest, targetingConfig)
        accountDao.save(account)

        and: "Mocked external request"
        storedCache.setTargetingResponse(bidRequest, targetingConfig)
        storedCache.setCachingResponse(NOT_FOUND_404)

        and: "Flash metrics"
        flushMetrics(prebidServerStoredCacheService)

        when: "PBS processes auction request"
        prebidServerStoredCacheService.sendAuctionRequest(bidRequest)

        then: "PBS should update error metrics"
        def metrics = prebidServerStoredCacheService.sendCollectedMetricsRequest()
        assert metrics[METRIC_CREATIVE_WRITE_ERR] == 1

        and: "No updates for success metrics"
        assert !metrics[METRIC_CREATIVE_WRITE_OK]
    }

    def "PBS should update metrics for new saved text storage cache when no cached requests"() {
        given: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(GOOGLE_GAID): randomIfa])
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
        assert metrics[METRIC_CREATIVE_SIZE_TEXT] == encodeBase64(encode(targetingResult).bytes).size()
        assert metrics[METRIC_CREATIVE_TTL_TEXT] == targetingConfig.cache.ttlSeconds
        assert metrics[METRIC_CREATIVE_WRITE_OK] == 1
    }

    def "PBS should update metrics for stored cached requests cache when proper record present"() {
        given: "Current value of metric prebid cache"
        def textInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, METRIC_CREATIVE_SIZE_TEXT)
        def ttlInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, METRIC_CREATIVE_TTL_TEXT)
        def writeInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, METRIC_CREATIVE_WRITE_OK)
        def readErrorInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, METRIC_CREATIVE_READ_ERR)
        def writeErrorInitialValue = getCurrentMetricValue(prebidServerStoredCacheService, METRIC_CREATIVE_WRITE_ERR)

        and: "Default BidRequest with cache and device info"
        def randomIfa = PBSUtils.randomString
        def bidRequest = getBidRequestForModuleCacheStorage(randomIfa)

        and: "Account with optable targeting module"
        def targetingConfig = OptableTargetingConfig.getDefault([(GOOGLE_GAID): randomIfa])
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
        assert metrics[METRIC_CREATIVE_READ_OK] == 1

        and: "No updates for new saved text storage metrics"
        assert metrics[METRIC_CREATIVE_SIZE_TEXT] == textInitialValue
        assert metrics[METRIC_CREATIVE_TTL_TEXT] == ttlInitialValue
        assert metrics[METRIC_CREATIVE_WRITE_OK] == writeInitialValue

        and: "No update for error metrics"
        assert metrics[METRIC_CREATIVE_READ_ERR] == readErrorInitialValue
        assert metrics[METRIC_CREATIVE_WRITE_ERR] == writeErrorInitialValue
    }

    private static BidRequest getBidRequestForModuleCacheStorage(String ifa) {
        BidRequest.defaultBidRequest.tap {
            it.enableCache()
            it.user = new User(id: PBSUtils.randomString, data: [Data.defaultData], eids: [Eid.defaultEid])
            it.device = new Device(geo: Geo.getFPDGeo(), ip: USA_IP.v4, ifa: ifa, os: ANDROID.value)
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
