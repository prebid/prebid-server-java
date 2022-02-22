package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class User {

    String id
    String buyeruid
    Integer yob
    String gender
    String language
    String keywords
    String customdata
    Geo geo
    List<Data> data
    UserExt ext

    static getDefaultUser() {
        new User(id: PBSUtils.randomString)
    }
}
