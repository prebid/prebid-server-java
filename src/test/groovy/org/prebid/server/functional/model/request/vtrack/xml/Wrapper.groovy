package org.prebid.server.functional.model.request.vtrack.xml

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class Wrapper {

    @JacksonXmlProperty(localName = "AdSystem")
    String adSystem
    @JacksonXmlProperty(localName = "VASTAdTagURI")
    String vastAdTagUri
    @JacksonXmlProperty(localName = "Impression")
    String impression
    @JacksonXmlProperty(localName = "Creatives")
    String creatives

    static Wrapper getDefaultWrapper(String payload) {
        new Wrapper().tap {
            adSystem = "prebid.org wrapper"
            vastAdTagUri = "<![CDATA[//$payload]]>"
            impression = " <![CDATA[ ]]> "
            creatives = ""
        }
    }
}
