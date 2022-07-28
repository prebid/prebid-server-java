package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class User {

    String id
    String buyeruid
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
}
