package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.ModuleName

//TODO remove if module hooks implementation codes will become consistent
enum ModuleHookImplementation {

    PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES("pb-richmedia-filter-all-processed-bid-responses-hook"),
    RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES("pb-response-correction-all-processed-bid-responses"),
    ORTB2_BLOCKING_BIDDER_REQUEST("ortb2-blocking-bidder-request"),
    ORTB2_BLOCKING_RAW_BIDDER_RESPONSE("ortb2-blocking-raw-bidder-response"),
    PB_REQUEST_CORRECTION_PROCESSED_AUCTION_REQUEST("pb-request-correction-processed-auction-request"),
    OPTABLE_TARGETING_PROCESSED_AUCTION_REQUEST("optable-targeting-processed-auction-request-hook"),
    PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST("pb-rule-engine-processed-auction-request")

    @JsonValue
    final String code

    ModuleHookImplementation(String code) {
        this.code = code
    }

    static ModuleHookImplementation forValue(ModuleName name, Stage stage) {
        values().find { it.code.contains(name.code) && it.code.contains(stage.value) }
    }
}
