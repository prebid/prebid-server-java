package org.prebid.server.functional.model.request.vtrack.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class Ad {

    @JacksonXmlProperty(localName = "Wrapper")
    Wrapper wrapper

    static Ad getDefaultAd(String payload) {
        new Ad().tap {
            wrapper = Wrapper.getDefaultWrapper(payload)
        }
    }
}
