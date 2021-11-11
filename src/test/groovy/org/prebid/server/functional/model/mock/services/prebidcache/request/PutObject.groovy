package org.prebid.server.functional.model.mock.services.prebidcache.request

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

import static Type.XML
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
class PutObject {

    Type type
    String value
    Integer ttlseconds
    String bidid
    String bidder
    Long timestamp

    static PutObject getDefaultPutObject(String creative, Type creativeType = XML) {
        new PutObject().tap {
            bidid = PBSUtils.randomNumber.toString()
            bidder = GENERIC.value
            type = creativeType
            ttlseconds = 10
            value = creative
        }
    }
}
