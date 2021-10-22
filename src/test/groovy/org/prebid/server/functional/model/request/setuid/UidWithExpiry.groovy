package org.prebid.server.functional.model.request.setuid

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import groovy.transform.ToString
import org.prebid.server.functional.model.response.setuid.ZonedDateTimeDeserializer

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class UidWithExpiry {

    String uid
    @JsonFormat(pattern = "yyyy-MM-dd'T'hh:mm:ss.SSS'Z'", timezone = "UTC")
    @JsonDeserialize(using = ZonedDateTimeDeserializer)
    ZonedDateTime expires
}
