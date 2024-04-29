package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Renderer {

    String name
    String version
    String url
    RendererData data
}
