package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
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
    OperationState mobile
    OperationState privacyPolicy
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

    static Site getRootFPDSite() {
        new Site().tap {
            id = PBSUtils.randomString
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
            cat = [PBSUtils.randomString]
            sectionCat = [PBSUtils.randomString]
            pageCat = [PBSUtils.randomString]
            page = PBSUtils.randomString
            ref = PBSUtils.randomString
            search = PBSUtils.randomString
            content = Content.FPDContent
            publisher = Publisher.FPDPublisher
            keywords = PBSUtils.randomString
            mobile = PBSUtils.getRandomEnum(OperationState)
            privacyPolicy = PBSUtils.getRandomEnum(OperationState)
            ext = SiteExt.FPDSiteExt
        }
    }

    static Site getConfigFPDSite() {
        new Site().tap {
            name = PBSUtils.randomString
            domain = PBSUtils.randomString
            cat = [PBSUtils.randomString, PBSUtils.randomString]
            sectionCat = [PBSUtils.randomString, PBSUtils.randomString]
            pageCat = [PBSUtils.randomString, PBSUtils.randomString]
            page = PBSUtils.randomString
            ref = PBSUtils.randomString
            search = PBSUtils.randomString
            keywords = PBSUtils.randomString
            ext = SiteExt.FPDSiteExt
        }
    }
}
