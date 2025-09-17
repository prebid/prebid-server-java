package org.prebid.server.functional.repository

import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.prebid.server.functional.model.db.Account
<<<<<<< HEAD
import org.prebid.server.functional.model.db.StoredProfileImp
import org.prebid.server.functional.model.db.StoredProfileRequest
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.repository.dao.AccountDao
<<<<<<< HEAD
import org.prebid.server.functional.repository.dao.ProfileImpDao
import org.prebid.server.functional.repository.dao.ProfileRequestDao
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.functional.repository.dao.StoredImpDao
import org.prebid.server.functional.repository.dao.StoredRequestDao
import org.prebid.server.functional.repository.dao.StoredResponseDao
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer

class HibernateRepositoryService {

    private static final String MY_SQL_DIALECT = "org.hibernate.dialect.MySQLDialect"
    private static final String POSTGRES_SQL_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"

    EntityManagerUtil entityManagerUtil
    AccountDao accountDao
    StoredImpDao storedImpDao
    StoredRequestDao storedRequestDao
    StoredResponseDao storedResponseDao
<<<<<<< HEAD
    ProfileImpDao profileImpDao
    ProfileRequestDao profileRequestDao
=======
>>>>>>> 04d9d4a13 (Initial commit)

    HibernateRepositoryService(JdbcDatabaseContainer container) {
        def jdbcUrl = container.jdbcUrl
        def user = container.username
        def pass = container.password
        def driver = container.driverClassName
        def dialect = container instanceof PostgreSQLContainer ? POSTGRES_SQL_DIALECT : MY_SQL_DIALECT

        SessionFactory sessionFactory = configureHibernate(jdbcUrl, dialect, user, pass, driver)
        entityManagerUtil = new EntityManagerUtil(sessionFactory)

        accountDao = new AccountDao(entityManagerUtil)
        storedImpDao = new StoredImpDao(entityManagerUtil)
        storedRequestDao = new StoredRequestDao(entityManagerUtil)
        storedResponseDao = new StoredResponseDao(entityManagerUtil)
<<<<<<< HEAD
        profileImpDao = new ProfileImpDao(entityManagerUtil)
        profileRequestDao = new ProfileRequestDao(entityManagerUtil)
=======
>>>>>>> 04d9d4a13 (Initial commit)
    }

    private static SessionFactory configureHibernate(String jdbcUrl,
                                                     String dialect,
                                                     String user,
                                                     String pass,
                                                     String driver) {
        def properties = new Properties()
        properties.setProperty("hibernate.connection.url", jdbcUrl)
        properties.setProperty("hibernate.dialect", dialect)
        properties.setProperty("hibernate.connection.username", user)
        properties.setProperty("hibernate.connection.password", pass)
        properties.setProperty("hibernate.connection.driver_class", driver)
        properties.setProperty("hibernate.show_sql", "false")
        properties.setProperty("hibernate.format_sql", "false")

        def configuration = new Configuration()
        configuration.addAnnotatedClass(Account)
        configuration.addAnnotatedClass(StoredImp)
        configuration.addAnnotatedClass(StoredRequest)
        configuration.addAnnotatedClass(StoredResponse)
<<<<<<< HEAD
        configuration.addAnnotatedClass(StoredProfileImp)
        configuration.addAnnotatedClass(StoredProfileRequest)
=======
>>>>>>> 04d9d4a13 (Initial commit)

        SessionFactory sessionFactory = configuration.addProperties(properties).buildSessionFactory()
        sessionFactory
    }

    void removeAllDatabaseData() {
        accountDao.removeAll()
        storedImpDao.removeAll()
        storedRequestDao.removeAll()
        storedResponseDao.removeAll()
<<<<<<< HEAD
        profileImpDao.removeAll()
        profileRequestDao.removeAll()
=======
>>>>>>> 04d9d4a13 (Initial commit)
    }
}
