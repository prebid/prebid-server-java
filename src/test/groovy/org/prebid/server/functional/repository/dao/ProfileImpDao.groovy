package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.StoredProfileImp
import org.prebid.server.functional.repository.EntityManagerUtil

class ProfileImpDao extends EntityDao<StoredProfileImp, Integer> {

    ProfileImpDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, StoredProfileImp)
    }
}
