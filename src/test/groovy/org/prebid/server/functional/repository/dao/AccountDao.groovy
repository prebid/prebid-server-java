package org.prebid.server.functional.repository.dao

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.repository.EntityManagerUtil

class AccountDao extends EntityDao<Account, Integer> {

    AccountDao(EntityManagerUtil entityManagerUtil) {
        super(entityManagerUtil, Account)
    }
}
