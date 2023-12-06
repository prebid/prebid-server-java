package org.prebid.server.functional.model.request

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class SecBrowsingTopic {

    String domain
    List<String> segments
    Integer taxonomyVersion
    Integer modelVersion

    static final SecBrowsingTopic defaultSetBrowsingTopic(Integer taxonomyVersion = PBSUtils.getRandomNumber(1, 10),
                                                          List<String> segments = [PBSUtils.randomNumber.toString(),
                                                                                   PBSUtils.randomNumber.toString()],
                                                          Integer modelVersion = PBSUtils.randomNumber) {
        new SecBrowsingTopic().tap {
            it.domain = PBSUtils.randomString
            it.segments = segments
            it.taxonomyVersion = taxonomyVersion
            it.modelVersion = modelVersion
        }
    }

    String getValidAsHeader(Boolean withEnd = false) {
        String header = "(${segments.join(' ')});v=chrome.1:$taxonomyVersion:$modelVersion, "
        return withEnd ? "$header, ();p=P000000000, " : header
    }

    String getInvalidAsHeader(Boolean withEnd = false) {
        def header = "(${segments.join(' ')});v=Something.1:$taxonomyVersion:$modelVersion"
        return withEnd ? "$header, ();p=P000000000, " : header
    }
}
