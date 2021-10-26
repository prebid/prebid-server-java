package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.S2sConfig
import org.prebid.server.functional.repository.EntityManagerUtil

class ConfigDao extends EntityDao<S2sConfig, Integer> {

    ConfigDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, S2sConfig)
    }
}
