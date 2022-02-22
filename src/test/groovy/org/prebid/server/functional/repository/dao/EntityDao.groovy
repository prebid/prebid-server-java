package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.repository.EntityManagerUtil

abstract class EntityDao<ENTITY, ID> {

    EntityManagerUtil emUtil

    Class<ENTITY> entityClass
    String entityClassName

    EntityDao(EntityManagerUtil entityManagerUtil, Class<ENTITY> entityClass) {
        this.emUtil = entityManagerUtil
        this.entityClass = entityClass
        this.entityClassName = entityClass.simpleName
    }

    ENTITY update(ENTITY entity) {
        emUtil.getResultWithinTx { it.merge(entity) }
    }

    ENTITY save(ENTITY entity) {
        emUtil.performWithinTx { it.persist(entity) }
        entity
    }

    ENTITY findById(ID id) {
        emUtil.getResultWithinTx { it.find(entityClass, id) }
    }

    List<ENTITY> findAll() {
        emUtil.getResultWithinTx { it.createQuery("SELECT e FROM $entityClassName e").resultList }
    }

    void remove(ENTITY entity) {
        emUtil.performWithinTx {
            ENTITY managedEntity = it.merge(entity)
            it.remove(managedEntity)
        }
    }

    void removeAll() {
        emUtil.performWithinTx { it.createQuery("DELETE FROM $entityClassName").executeUpdate() }
    }
}
