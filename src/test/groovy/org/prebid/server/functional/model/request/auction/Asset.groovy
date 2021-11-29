package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Asset {

    Integer id
    Integer required
    AssetTitle title
    AssetImage img
    AssetVideo video
    AssetData data

    static Asset getAssetTitle() {
        new Asset().tap {
            id = 1
            required = 1
            title = new AssetTitle(len: PBSUtils.randomNumber)
        }
    }

    static Asset getAssetImg() {
        new Asset().tap {
            id = 2
            required = 1
            img = new AssetImage(type: 3, w: PBSUtils.randomNumber, h: PBSUtils.randomNumber)
        }
    }

    static Asset getAssetData() {
        new Asset().tap {
            id = 3
            required = 1
            data = new AssetData(type: 1)
        }
    }
}
