package org.prebid.server.functional.model.mock.services.generalplanner

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.ResponseModel
import org.prebid.server.functional.model.deals.lineitem.LineItem

class PlansResponse implements ResponseModel {

    List<LineItem> lineItems

    static PlansResponse getDefaultPlansResponse(String accountId) {
        new PlansResponse(lineItems: [LineItem.getDefaultLineItem(accountId)])
    }

    @JsonValue
    List<LineItem> getLineItems() {
        lineItems
    }
}
