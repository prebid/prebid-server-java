package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class UserExtData {

    List<String> keywords
    String buyeruid
    List<String> buyeruids
    Geo geo

    static UserExtData getFPDUserExtData() {
        new UserExtData().tap {
            keywords = [PBSUtils.randomString]
            buyeruid = PBSUtils.randomString
            buyeruids = [PBSUtils.randomString]
        }
    }
}
