package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@EqualsAndHashCode
class User {

    String id
    String buyerUid
    Integer yob
    String gender
    String keywords
    List<String> kwarray
    String customdata
    Geo geo
    List<Data> data
    String consent
    List<Eid> eids
    UserExt ext

    static getDefaultUser() {
        new User(id: PBSUtils.randomString)
    }

    static User getRootFPDUser() {
        new User().tap {
            id = PBSUtils.randomString
            yob = PBSUtils.randomNumber
            gender = PBSUtils.randomString
            keywords = PBSUtils.randomString
            geo = Geo.FPDGeo
            ext = UserExt.FPDUserExt
        }
    }

    static User getConfigFPDUser() {
        new User().tap {
            yob = PBSUtils.randomNumber
            gender = PBSUtils.randomString
            keywords = PBSUtils.randomString
            ext = UserExt.FPDUserExt
        }
    }
}
