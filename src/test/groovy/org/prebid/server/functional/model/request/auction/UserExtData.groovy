package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class UserExtData {

    List<String> keywords
    String buyerUid
    List<String> buyerUids
    Geo geo

    static UserExtData getFPDUserExtData() {
        new UserExtData().tap {
            keywords = [PBSUtils.randomString]
            buyerUid = PBSUtils.randomString
            buyerUids = [PBSUtils.randomString]
        }
    }
}
