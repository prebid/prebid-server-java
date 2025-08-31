package org.prebid.server.functional.model.request.profile

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

abstract class Profile<T> {

    @JsonIgnore
    String accountId
    @JsonIgnore
    String name
    ProfileType type
    @JsonProperty("mergeprecedence")
    ProfileMergePrecedence mergePrecedence
    T body

    String getRecordName() {
        "${accountId}-${name}"
    }

    String getFileName() {
        "${recordName}.json"
    }
}
