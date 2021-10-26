package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Native {

    @JsonSerialize(using = Request.NativeSerializer)
    @JsonDeserialize(using = Request.NativeDeserializer)
    Request request
    String ver
    List<Integer> api
    List<Integer> battr
}
