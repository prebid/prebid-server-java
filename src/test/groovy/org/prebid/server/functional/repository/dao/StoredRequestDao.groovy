package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.repository.EntityManagerUtil

class StoredRequestDao extends EntityDao<StoredRequest, Integer> {

    StoredRequestDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, StoredRequest)
    }
}
