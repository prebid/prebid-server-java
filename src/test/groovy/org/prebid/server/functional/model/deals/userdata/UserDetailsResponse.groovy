package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString
import org.prebid.server.functional.model.ResponseModel

@ToString(includeNames = true, ignoreNulls = true)
class UserDetailsResponse implements ResponseModel {

    User user

    static UserDetailsResponse getDefaultUserResponse(User user = User.defaultUser) {
        new UserDetailsResponse(user: user)
    }
}
