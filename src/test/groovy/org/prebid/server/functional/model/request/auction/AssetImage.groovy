package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class AssetImage {

    Integer type
    String url
    Integer w
    Integer wmin
    Integer h
    Integer hmin
    List<String> mimes

    static AssetImage getDefaultAssetImage() {
        new AssetImage(type: 1, url: PBSUtils.randomString)
    }
}
