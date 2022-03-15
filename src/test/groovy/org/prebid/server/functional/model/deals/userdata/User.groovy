package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class User {

    List<UserData> data
    UserExt ext

    static getDefaultUser() {
        new User(data: [UserData.defaultUserData],
                ext: UserExt.defaultUserExt
        )
    }
}
