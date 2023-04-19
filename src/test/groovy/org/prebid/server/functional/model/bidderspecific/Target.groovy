package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Target {

    List<String> page
    List<String> keywords
    List<String> buyeruid
    List<String> buyeruids
    List<String> iab
    List<String> sectioncat
    List<String> pagecat
    List<String> ref
    List<String> search
    List<String> domain
    List<String> cat
    List<String> language
}
