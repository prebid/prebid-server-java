package org.prebid.server.functional.model.request.vtrack

import groovy.transform.ToString
import org.prebid.server.functional.model.mock.services.prebidcache.request.PutObject

@ToString(includeNames = true, ignoreNulls = true)
class VtrackRequest {

    List<PutObject> puts

    static VtrackRequest getDefaultVtrackRequest(String creative) {
        def vtrack = new VtrackRequest()
        vtrack.addPutObject(PutObject.getDefaultPutObject(creative))
        vtrack
    }

    void addPutObject(PutObject putObject) {
        if (this.puts == null) {
            this.puts = []
        }
        this.puts.add(putObject)
    }
}
