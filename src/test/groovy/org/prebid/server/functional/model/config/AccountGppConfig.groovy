package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.PrivacyModule

@ToString(includeNames = true, ignoreNulls = true)
class AccountGppConfig {

    PrivacyModule code
    SidsConfig config
    Boolean enabled
    ModuleConfig moduleConfig
}
