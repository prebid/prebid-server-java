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
    OperationState mobile
    OperationState privacypolicy
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

    static Site getRootFPDSite() {
        new Site().tap {
            id = PBSUtils.randomString
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
            cat = [PBSUtils.randomString]
            sectioncat = [PBSUtils.randomString]
            pagecat = [PBSUtils.randomString]
            page = PBSUtils.randomString
            ref = PBSUtils.randomString
            search = PBSUtils.randomString
            content = Content.FPDContent
            publisher = Publisher.FPDPublisher
            keywords = PBSUtils.randomString
            mobile = PBSUtils.getRandomEnum(OperationState)
            privacypolicy = PBSUtils.getRandomEnum(OperationState)
            ext = SiteExt.FPDSiteExt
        }
    }

    static Site getConfigFPDSite() {
        new Site().tap {
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
            cat = [PBSUtils.randomString, PBSUtils.randomString]
            sectioncat = [PBSUtils.randomString, PBSUtils.randomString]
            pagecat = [PBSUtils.randomString, PBSUtils.randomString]
            page = PBSUtils.randomString
            ref = PBSUtils.randomString
            search = PBSUtils.randomString
            keywords = PBSUtils.randomString
            ext = SiteExt.FPDSiteExt
        }
    }
}
