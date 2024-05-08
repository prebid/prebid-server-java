package org.prebid.server.functional.tests.postgres

import org.prebid.server.functional.repository.HibernateRepositoryService
import org.prebid.server.functional.repository.dao.AccountDao
import org.prebid.server.functional.repository.dao.StoredImpDao
import org.prebid.server.functional.repository.dao.StoredRequestDao
import org.prebid.server.functional.repository.dao.StoredResponseDao
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsConfig
import org.prebid.server.functional.tests.BaseSpec
import org.testcontainers.lifecycle.Startables

class PostgresBaseSpec extends BaseSpec {

    protected static HibernateRepositoryService repository
    protected static AccountDao accountDao
    protected static StoredImpDao storedImpDao
    protected static StoredRequestDao storedRequestDao
    protected static StoredResponseDao storedResponseDao

    protected PrebidServerService pbsServiceWithPostgres

    void setup() {
        Startables.deepStart(Dependencies.postgresqlContainer)
                .join()
        repository = new HibernateRepositoryService(Dependencies.postgresqlContainer)
        accountDao = repository.accountDao
        storedImpDao = repository.storedImpDao
        storedRequestDao = repository.storedRequestDao
        storedResponseDao = repository.storedResponseDao
        pbsServiceWithPostgres = pbsServiceFactory.getService(PbsConfig.postgreSqlConfig)
    }

    void cleanup() {
        Dependencies.postgresqlContainer.stop()
    }
}
