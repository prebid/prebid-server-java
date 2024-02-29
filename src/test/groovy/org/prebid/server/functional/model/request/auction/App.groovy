package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class App {

    String id
    String name
    String bundle
    String domain
    String storeUrl
    Integer catTax
    List<String> cat
    List<String> sectionCat
    List<String> pageCat
    String ver
    Integer privacyPolicy
    Integer paid
    Publisher publisher
    Content content
    String keywords
    List<String> kwArray
    String inventoryPartnerDomain
    AppExt ext

    static App getDefaultApp() {
        new App(id: PBSUtils.randomString,
                publisher: Publisher.defaultPublisher)
    }
}
