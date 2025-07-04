package org.prebid.server.functional.model.request.profile

import com.fasterxml.jackson.annotation.JsonIgnore

abstract class Profile<T> {

    @JsonIgnore
    String accountId
    @JsonIgnore
    String name
    ProfileType type
    ProfileMergePrecedence mergePrecedence
    T body

    String getRecordName() {
        "${accountId}_${name}"
    }

    String getFileName() {
        "${recordName}.json"
    }
}
