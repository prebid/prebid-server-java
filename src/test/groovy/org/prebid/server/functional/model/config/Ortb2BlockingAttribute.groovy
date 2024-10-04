package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum Ortb2BlockingAttribute {

    BADV('badv'),
    BAPP('bapp'),
    BANNER_BATTR('battr'),
    VIDEO_BATTR('battr'),
    AUDIO_BATTR('battr'),
    BCAT('bcat'),
    BTYPE('btype')

    @JsonValue
    final String value

    Ortb2BlockingAttribute(String value) {
        this.value = value
    }
}
