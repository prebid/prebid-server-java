package org.prebid.server.functional.tests

import org.prebid.server.functional.repository.HibernateRepositoryService
import org.prebid.server.functional.repository.dao.AccountDao
import org.prebid.server.functional.repository.dao.ConfigDao
import org.prebid.server.functional.repository.dao.StoredImpDao
import org.prebid.server.functional.repository.dao.StoredRequestDao
import org.prebid.server.functional.repository.dao.StoredResponseDao
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.testcontainers.scaffolding.PrebidCache
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Specification

@PBSTest
abstract class BaseSpec extends Specification {

    protected static final ObjectMapperWrapper mapper = Dependencies.objectMapperWrapper
    protected static final PbsServiceFactory pbsServiceFactory = new PbsServiceFactory(Dependencies.networkServiceContainer, Dependencies.objectMapperWrapper)
    protected static final Bidder bidder = new Bidder(Dependencies.networkServiceContainer, Dependencies.objectMapperWrapper)
    protected static final PrebidCache prebidCache = new PrebidCache(Dependencies.networkServiceContainer, Dependencies.objectMapperWrapper)
    protected static final PrebidServerService defaultPbsService = pbsServiceFactory.getService([:])

    protected static final HibernateRepositoryService repository = new HibernateRepositoryService(Dependencies.mysqlContainer)
    protected static final AccountDao accountDao = repository.accountDao
    protected static final ConfigDao configDao = repository.configDao
    protected static final StoredImpDao storedImp = repository.storedImpDao
    protected static final StoredRequestDao storedRequestDao = repository.storedRequestDao
    protected static final StoredResponseDao storedResponseDao = repository.storedResponseDao

    protected static final int MIN_TIMEOUT = 1000
    protected static final int MAX_TIMEOUT = 5000

    def setupSpec() {
        prebidCache.setResponse()
        bidder.setResponse()
    }

    def cleanupSpec() {
        bidder.reset()
        prebidCache.reset()
        repository.removeAllDatabaseData()
        pbsServiceFactory.stopContainers()
    }

    protected static int getRandomTimeout() {
        PBSUtils.getRandomNumber(MIN_TIMEOUT, MAX_TIMEOUT)
    }

    protected static Number getCurrentMetricValue(String name) {
        def response = defaultPbsService.sendCollectedMetricsRequest()
        response[name] ?: 0
    }

    protected static void flushMetrics() {
        // flushing PBS metrics by receiving collected metrics so that each new test works with a fresh state
        defaultPbsService.sendCollectedMetricsRequest()
    }
}
