package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

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
    DoohExt ext

    static Dooh getDefaultDooh() {
        new Dooh(id: PBSUtils.randomString,
                venueType: [PBSUtils.randomString],
                publisher: Publisher.defaultPublisher)
    }
}
