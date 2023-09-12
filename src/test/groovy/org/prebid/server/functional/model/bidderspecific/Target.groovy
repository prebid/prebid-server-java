package org.prebid.server.functional.model.bidderspecific

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Target {

    List<String> page
    List<String> keywords
    List<String> buyerUid
    List<String> buyerUids
    List<String> iab
    List<String> sectionCat
    List<String> pageCat
    List<String> ref
    List<String> search
    List<String> domain
    List<String> cat
    List<String> language
}
