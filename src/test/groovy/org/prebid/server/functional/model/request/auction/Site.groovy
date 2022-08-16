package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Site {

    String id
    String name
    String domain
    Integer cattax
    List<String> cat
    List<String> sectioncat
    List<String> pagecat
    String page
    String ref
    String search
    Integer mobile
    Integer privacypolicy
    Publisher publisher
    Content content
    String keywords
    List<String> kwarray
    SiteExt ext

    static Site getDefaultSite() {
        new Site().tap {
            page = PBSUtils.randomString
            publisher = Publisher.defaultPublisher
        }
    }
}
