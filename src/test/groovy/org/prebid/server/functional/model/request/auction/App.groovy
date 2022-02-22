package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class App {

    String id
    String name
    String bundle
    String domain
    String storeurl
    List<String> cat
    List<String> sectioncat
    List<String> pagecat
    String ver
    Integer privacypolicy
    Integer paid
    Publisher publisher
    Content content
    String keywords
    AppExt ext

    static App getDefaultApp() {
        new App().tap {
            id = PBSUtils.randomString
            publisher = Publisher.defaultPublisher
        }
    }
}
