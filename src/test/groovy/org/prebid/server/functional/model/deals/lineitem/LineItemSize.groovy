package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.ToString

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS

@ToString(includeNames = true)
@JsonInclude(content = ALWAYS)
class LineItemSize {

    Integer w
    Integer h

    static getDefaultLineItemSize() {
        new LineItemSize(w: 300,
                h: 250
        )
    }
}
