package org.prebid.server.functional.tests.module.optabletargeting

import org.prebid.server.functional.model.config.IdentifierType
import org.prebid.server.functional.model.config.OperatingSystem
import org.prebid.server.functional.model.config.OptableTargetingConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.PublicCountryIp
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.testcontainers.scaffolding.StoredCache
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.Metrics
import org.prebid.server.functional.util.PBSUtils

import static org.apache.commons.codec.binary.Base64.encodeBase64
import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404
import static org.prebid.server.functional.model.config.ModuleHookImplementation.OPTABLE_TARGETING_PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.model.config.ModuleName.OPTABLE_TARGETING
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class CacheStorageSpec extends ModuleBaseSpec {

    private static final StoredCache storedCache = new StoredCache(networkServiceContainer)

    def setup() {
        storedCache.reset()
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
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for new saved text storage cache"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
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
        storedCache.setCachingResponse(NOT_FOUND_404)

        and: "Flash metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should update error metrics"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.writeErr(OPTABLE_TARGETING)] == 1

        and: "No updates for success metrics"
        assert !metrics[Metrics.Cache.writeOk(OPTABLE_TARGETING)]
    }

    def "PBS should update metrics for new saved text storage cache when no cached requests"() {
        given: "Current value of metric prebid cache"
        def okInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.writeOk(OPTABLE_TARGETING))

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
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for new saved text storage cache"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[Metrics.Cache.creativeSizeText(OPTABLE_TARGETING)] == new String(encodeBase64(encode(targetingResult).bytes)).size()
        assert metrics[Metrics.Cache.writeOk(OPTABLE_TARGETING)] == okInitialValue + 1

        and: "PBS should include histogram metric"
        assert metrics[Metrics.Cache.creativeTtlText(OPTABLE_TARGETING)]
    }

    def "PBS should update metrics for stored cached requests cache when proper record present"() {
        given: "Current value of metric prebid cache"
        def textInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.creativeSizeText(OPTABLE_TARGETING))
        def ttlInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.creativeTtlText(OPTABLE_TARGETING))
        def writeInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.writeOk(OPTABLE_TARGETING))
        def readErrorInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.readErr(OPTABLE_TARGETING))
        def writeErrorInitialValue = getCurrentMetricValue(pbsServiceWithMultipleModules, Metrics.Cache.writeErr(OPTABLE_TARGETING))

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
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should update metrics for stored cached requests"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
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

        getAccountWithModuleConfig(bidRequest.accountId, [OPTABLE_TARGETING_PROCESSED_AUCTION_REQUEST]).tap {
            it.config.hooks.modules.optableTargeting = optableTargetingConfig
        }
    }
}
