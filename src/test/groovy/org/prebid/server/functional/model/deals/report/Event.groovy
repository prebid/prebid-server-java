package org.prebid.server.functional.model.deals.report

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Event {

    String type
    Long count
}
