package org.prebid.server.functional.model.request.auction

<<<<<<< HEAD
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
=======
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

>>>>>>> 04d9d4a13 (Initial commit)
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
