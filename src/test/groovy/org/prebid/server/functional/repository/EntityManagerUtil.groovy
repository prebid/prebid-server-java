package org.prebid.server.functional.repository

import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory

import java.util.function.Consumer
import java.util.function.Function

class EntityManagerUtil {

    private SessionFactory sessionFactory

    EntityManagerUtil(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory
    }

    void performWithinTx(Consumer<EntityManager> entityManagerConsumer) {
        EntityManager entityManager = sessionFactory.createEntityManager()
        entityManager.transaction.begin()
        try {
            entityManagerConsumer.accept(entityManager)
            entityManager.transaction.commit()
        } catch (all) {
            entityManager.transaction.rollback()
            throw new DaoException("Error performing dao operation. Transaction is rolled back!", all)
        } finally {
            entityManager.close()
        }
    }

    def <T> T getResultWithinTx(Function<EntityManager, T> entityManagerFunction) {
        EntityManager entityManager = sessionFactory.createEntityManager()
        entityManager.transaction.begin()
        try {
            T result = entityManagerFunction.apply(entityManager)
            entityManager.transaction.commit()
            return result
        } catch (all) {
            entityManager.transaction.rollback()
            throw new DaoException("Error performing dao operation. Transaction is rolled back!", all)
        } finally {
            entityManager.close()
        }
    }

    class DaoException extends RuntimeException {

        DaoException(String message, Throwable cause) {
            super(message, cause)
        }
    }
}
