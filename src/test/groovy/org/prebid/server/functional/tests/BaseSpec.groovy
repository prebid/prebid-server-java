package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.repository.HibernateRepositoryService
import org.prebid.server.functional.repository.dao.AccountDao
import org.prebid.server.functional.repository.dao.ConfigDao
import org.prebid.server.functional.repository.dao.StoredImpDao
import org.prebid.server.functional.repository.dao.StoredRequestDao
import org.prebid.server.functional.repository.dao.StoredResponseDao
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.ContainerFactory
import org.prebid.server.functional.testcontainers.ContainerWrapper
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.testcontainers.scaffolding.PrebidCache
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils
import org.testcontainers.containers.GenericContainer
import spock.lang.Retry
import spock.lang.Specification

import static java.math.RoundingMode.DOWN
import static org.prebid.server.functional.util.SystemProperties.DEFAULT_TIMEOUT

@Retry(count = 2, mode = Retry.Mode.SETUP_FEATURE_CLEANUP) // TODO decide whether it's necessary
abstract class BaseSpec extends Specification implements ObjectMapperWrapper {

    protected static final Bidder bidder = new Bidder(Dependencies.networkServiceContainer)
    protected static final PrebidCache prebidCache = new PrebidCache(Dependencies.networkServiceContainer)

    protected static final HibernateRepositoryService repository = new HibernateRepositoryService(Dependencies.mysqlContainer)

    protected static final AccountDao accountDao = repository.accountDao
    protected static final ConfigDao configDao = repository.configDao
    protected static final StoredImpDao storedImp = repository.storedImpDao
    protected static final StoredRequestDao storedRequestDao = repository.storedRequestDao
    protected static final StoredResponseDao storedResponseDao = repository.storedResponseDao
    protected static final int MAX_TIMEOUT = MIN_TIMEOUT + 1000

    private static final int MIN_TIMEOUT = DEFAULT_TIMEOUT

    private static final int DEFAULT_TARGETING_PRECISION = 1

    private static final ContainerFactory containerFactory = new ContainerFactory()
    private static final ThreadLocal<Set<ContainerWrapper>> acquiredContainers = ThreadLocal.withInitial { [] } as ThreadLocal<Set<ContainerWrapper>>

    def setupSpec() {
        prebidCache.setResponse()
        bidder.setResponse()
    }

    def cleanupSpec() {
        // TODO should be removed because of simultaneous tests execution.
        //        bidder.reset()
        //        prebidCache.reset()
        //        repository.removeAllDatabaseData()
    }

    def cleanup() {
        releaseContainers()
    }

    protected static int getRandomTimeout() {
        PBSUtils.getRandomNumber(MIN_TIMEOUT, MAX_TIMEOUT)
    }

    protected static Number getCurrentMetricValue(PrebidServerService pbsService, String name) {
        def response = pbsService.sendCollectedMetricsRequest()
        response[name] ?: 0
    }

    protected static void flushMetrics(PrebidServerService pbsService) {
        // flushing PBS metrics by receiving collected metrics so that each new test works with a fresh state
        pbsService.sendCollectedMetricsRequest()
    }

    protected static List<String> getLogsByText(List<String> logs, String text) {
        logs.findAll { it.contains(text) }
    }

    protected static String getRoundedTargetingValueWithDefaultPrecision(BigDecimal value) {
        "${value.setScale(DEFAULT_TARGETING_PRECISION, DOWN)}0"
    }

    private <T extends GenericContainer> ContainerWrapper acquireContainer(T container, Map<String, String> config) {
        containerFactory.acquireContainer(container, config).tap {
            acquiredContainers.get().add(it)
        }
    }

    protected PrebidServerService getPbsService(Map<String, String> config) {
        PrebidServerContainer prebidServerContainer = new PrebidServerContainer(config)
        new PrebidServerService(acquireContainer(prebidServerContainer, config)).tap { pbsService ->
            // request to "warm up" a PBS service
            try {
                pbsService.sendAuctionRequest(BidRequest.defaultBidRequest)
            } catch(Exception ignored) {}
        }
    }

    protected PrebidServerService getDefaultPbsService() {
        getPbsService([:])
    }

    private void releaseContainers() {
        acquiredContainers.get()
                          .each { containerFactory.releaseContainer(it) }
                          .clear()
    }
}
