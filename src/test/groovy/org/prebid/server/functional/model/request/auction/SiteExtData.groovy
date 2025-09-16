package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class SiteExtData {

    String id
    String language
    Publisher publisher
    String privacypolicy
    OperationState mobile
    Content content

    static SiteExtData getFPDSiteExtData() {
        new SiteExtData(language: PBSUtils.randomString)
    }
}
