package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Privacy {

    PrivacyTcf tcf
    PrivacyCcpa ccpa
    PrivacyCoppa coppa
}
