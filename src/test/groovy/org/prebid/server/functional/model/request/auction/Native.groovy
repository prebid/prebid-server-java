package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import groovy.transform.ToString
import org.prebid.server.functional.testcontainers.Dependencies

@ToString(includeNames = true, ignoreNulls = true)
class Native {

    NativeRequest request
    String ver
    List<Integer> api
    List<Integer> battr

    static Native getDefaultNative(){
        new Native(request: NativeRequest.nativeRequest)
    }

    @JsonGetter("request")
    String getRequest() {
        Dependencies.objectMapperWrapper.encode(request)
    }

    @JsonSetter("request")
    void getRequest(String request) {
        this.request = Dependencies.objectMapperWrapper.decode(request, NativeRequest)
    }

}
