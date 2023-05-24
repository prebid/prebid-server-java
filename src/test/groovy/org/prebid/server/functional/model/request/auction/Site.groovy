package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Site {

    String id
    String name
    String domain
    Integer catTax
    List<String> cat
    List<String> sectionCat
    List<String> pageCat
    String page
    String ref
    String search
    Integer mobile
    Integer privacyPolicy
    Publisher publisher
    Content content
    String keywords
    List<String> kwArray
    String inventoryPartnerDomain
    SiteExt ext

    static Site getDefaultSite() {
        new Site().tap {
            page = PBSUtils.randomString
            publisher = Publisher.defaultPublisher
        }
    }
}
