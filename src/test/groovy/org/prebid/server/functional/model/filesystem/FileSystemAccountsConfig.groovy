package org.prebid.server.functional.model.filesystem

import groovy.transform.ToString
import org.prebid.server.functional.model.config.AccountConfig

@ToString(includeNames = true, ignoreNulls = true)
class FileSystemAccountsConfig {

    List<AccountConfig> accounts
}
