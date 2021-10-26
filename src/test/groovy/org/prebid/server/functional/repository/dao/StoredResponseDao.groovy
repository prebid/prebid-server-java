package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.repository.EntityManagerUtil

class StoredResponseDao extends EntityDao<StoredResponse, Integer> {

    StoredResponseDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, StoredResponse)
    }
}
