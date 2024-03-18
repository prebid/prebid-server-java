package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.util.privacy.model.State

enum Country {

    USA("USA","US"),
    CAN("CAN","CAN"),
    MULTIPLE("*","*")

    @JsonValue
    final String ISOAlpha3

    final String ISOAlpha2

    Country(String ISOAlpha3,String ISOAlpha2) {
        this.ISOAlpha3 = ISOAlpha3
        this.ISOAlpha2 = ISOAlpha2
    }

    @Override
    String toString() {
        ISOAlpha3
    }

    String withState(State state) {
        return "${ISOAlpha3}.${state.abbreviation}".toString()
    }
}
