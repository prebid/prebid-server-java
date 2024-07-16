package org.prebid.server.functional.model.request.cache

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.response.auction.Bid

@ToString(includeNames = true, ignoreNulls = true)
class CacheBid extends Bid {

    List<Asset> assets

    CacheBid() {
    }

    // required for deserialize response in string
    CacheBid(String assets) {
        this.assets = decode(assets, CacheBid).assets
    }
}
