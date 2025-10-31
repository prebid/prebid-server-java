package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum ModuleName {

    PB_RICHMEDIA_FILTER("pb-richmedia-filter"),
    PB_RESPONSE_CORRECTION ("pb-response-correction"),
    ORTB2_BLOCKING("ortb2-blocking"),
    PB_REQUEST_CORRECTION('pb-request-correction'),
    OPTABLE_TARGETING('optable-targeting'),
    PB_RULE_ENGINE('pb-rule-engine')

    @JsonValue
    final String code

    ModuleName(String code) {
        this.code = code
    }

    static ModuleName forValue(ModuleHookImplementation moduleHook) {
        values().find { moduleHook.code.contains(it.code) }
    }
}
