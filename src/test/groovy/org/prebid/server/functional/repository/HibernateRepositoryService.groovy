package org.prebid.server.functional.repository

import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.S2sConfig
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.repository.dao.AccountDao
import org.prebid.server.functional.repository.dao.ConfigDao
import org.prebid.server.functional.repository.dao.StoredImpDao
import org.prebid.server.functional.repository.dao.StoredRequestDao
import org.prebid.server.functional.repository.dao.StoredResponseDao
import org.testcontainers.containers.MySQLContainer

class HibernateRepositoryService {

    EntityManagerUtil entityManagerUtil
    AccountDao accountDao
    ConfigDao configDao
    StoredImpDao storedImpDao
    StoredRequestDao storedRequestDao
    StoredResponseDao storedResponseDao

    HibernateRepositoryService(MySQLContainer container) {
        def jdbcUrl = container.jdbcUrl
        def user = container.username
        def pass = container.password
        def driver = container.driverClassName
        SessionFactory sessionFactory = configureHibernate(jdbcUrl, user, pass, driver)
        entityManagerUtil = new EntityManagerUtil(sessionFactory)

        accountDao = new AccountDao(entityManagerUtil)
        configDao = new ConfigDao(entityManagerUtil)
        storedImpDao = new StoredImpDao(entityManagerUtil)
        storedRequestDao = new StoredRequestDao(entityManagerUtil)
        storedResponseDao = new StoredResponseDao(entityManagerUtil)
    }

    private static SessionFactory configureHibernate(String jdbcUrl, String user, String pass, String driver) {
        def properties = new Properties()
        properties.setProperty("hibernate.connection.url", jdbcUrl)
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
        properties.setProperty("hibernate.connection.username", user)
        properties.setProperty("hibernate.connection.password", pass)
        properties.setProperty("hibernate.connection.driver_class", driver)
        properties.setProperty("hibernate.show_sql", "false")
        properties.setProperty("hibernate.format_sql", "false")

        def configuration = new Configuration()
        configuration.addAnnotatedClass(Account)
        configuration.addAnnotatedClass(S2sConfig)
        configuration.addAnnotatedClass(StoredImp)
        configuration.addAnnotatedClass(StoredRequest)
        configuration.addAnnotatedClass(StoredResponse)

        SessionFactory sessionFactory = configuration.addProperties(properties).buildSessionFactory()
        sessionFactory
    }

    void removeAllDatabaseData() {
        accountDao.removeAll()
        configDao.removeAll()
        storedImpDao.removeAll()
        storedRequestDao.removeAll()
        storedResponseDao.removeAll()
    }
}
