package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Dooh {
    
    String id
    String name
    List<String> venueType
    Integer venueTypeTax
    Publisher publisher
    String domain
    String keywords
    Content content
}
