package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.StoredProfileRequest
import org.prebid.server.functional.repository.EntityManagerUtil

class ProfileRequestDao extends EntityDao<StoredProfileRequest, Integer> {

    ProfileRequestDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, StoredProfileRequest)
    }
}
