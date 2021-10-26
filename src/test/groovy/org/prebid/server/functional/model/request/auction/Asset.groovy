package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Asset {

    Integer id
    Integer required
    AssetTitle title
    AssetImage img
    AssetVideo video
    AssetData data
}
