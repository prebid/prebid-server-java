package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserDetails {

    List<UserData> userData
    List<String> fcapIds
}
