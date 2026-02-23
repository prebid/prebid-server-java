package org.prebid.server.functional.model.config

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Audience {

    String provider
    List<AudienceId> ids
}
