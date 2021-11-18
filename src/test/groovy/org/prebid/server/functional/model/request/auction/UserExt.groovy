package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.privacy.BuildableConsentString

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    BuildableConsentString consent
}
