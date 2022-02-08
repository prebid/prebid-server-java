package org.prebid.server.functional.model.request.dealsupdate

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.CREATE_REPORT
import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.INVALIDATE_LINE_ITEMS
import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.REGISTER_INSTANCE
import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.RESET_ALERT_COUNT
import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.SEND_REPORT
import static org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest.Action.UPDATE_LINE_ITEMS

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class ForceDealsUpdateRequest {

    String actionName

    static ForceDealsUpdateRequest getUpdateLineItemsRequest() {
        new ForceDealsUpdateRequest(actionName: UPDATE_LINE_ITEMS.name())
    }

    static ForceDealsUpdateRequest getSendReportRequest() {
        new ForceDealsUpdateRequest(actionName: SEND_REPORT.name())
    }

    static ForceDealsUpdateRequest getRegisterInstanceRequest() {
        new ForceDealsUpdateRequest(actionName: REGISTER_INSTANCE.name())
    }

    static ForceDealsUpdateRequest getResetAlertCountRequest() {
        new ForceDealsUpdateRequest(actionName: RESET_ALERT_COUNT.name())
    }

    static ForceDealsUpdateRequest getCreateReportRequest() {
        new ForceDealsUpdateRequest(actionName: CREATE_REPORT.name())
    }

    static ForceDealsUpdateRequest getInvalidateLineItemsRequest() {
        new ForceDealsUpdateRequest(actionName: INVALIDATE_LINE_ITEMS.name())
    }

    private enum Action {

        UPDATE_LINE_ITEMS, SEND_REPORT, REGISTER_INSTANCE, RESET_ALERT_COUNT, CREATE_REPORT, INVALIDATE_LINE_ITEMS
    }
}
