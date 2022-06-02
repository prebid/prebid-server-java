package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import groovy.transform.ToString
import org.prebid.server.functional.util.ObjectMapperWrapper

@ToString(includeNames = true, ignoreNulls = true)
class Native implements ObjectMapperWrapper {

    Request request
    String ver
    List<Integer> api
    List<Integer> battr

    @JsonGetter("request")
    String getRequest() {
        encode(request)
    }

    @JsonSetter("request")
    void getRequest(String request) {
        this.request = decode(request, Request)
    }
}
