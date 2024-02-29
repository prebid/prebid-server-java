package org.prebid.server.functional.model.bidderspecific

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class Rp {

    Integer sizeId
    String mime
    Integer zoneId
    Target target
    Track track
    Integer accountId
    Integer siteId
}
