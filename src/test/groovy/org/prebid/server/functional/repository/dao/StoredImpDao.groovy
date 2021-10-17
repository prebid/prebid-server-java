package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.repository.EntityManagerUtil

class StoredImpDao extends EntityDao<StoredImp, Integer> {

    StoredImpDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, StoredImp)
    }

}
