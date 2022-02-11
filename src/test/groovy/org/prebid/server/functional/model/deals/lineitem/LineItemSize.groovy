package org.prebid.server.functional.model.deals.lineitem

import groovy.transform.ToString

@ToString(includeNames = true)
class LineItemSize {

    Integer w
    Integer h

    static getDefaultLineItemSize() {
        new LineItemSize(w: 300,
                h: 250
        )
    }
}
