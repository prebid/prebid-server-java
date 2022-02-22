package org.prebid.server.functional.model.request.vtrack.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "VAST")
class Vast {

    @JacksonXmlProperty(isAttribute = true)
    String version
    @JacksonXmlProperty(localName = "Ad")
    Ad ad

    static Vast getDefaultVastModel(String payload) {
        new Vast().tap {
            version = "3.0"
            ad = Ad.getDefaultAd(payload)
        }
    }
}
