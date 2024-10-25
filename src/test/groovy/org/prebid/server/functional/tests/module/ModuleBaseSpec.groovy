package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.tests.BaseSpec

import static org.prebid.server.functional.model.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES

class ModuleBaseSpec extends BaseSpec {

    def setupSpec() {
        prebidCache.setResponse()
        bidder.setResponse()
    }

    def cleanupSpec() {
        bidder.reset()
        prebidCache.reset()
        repository.removeAllDatabaseData()
    }

    protected static Map<String, String> getResponseCorrectionConfig(Endpoint endpoint = OPENRTB2_AUCTION) {
        ["hooks.${PB_RESPONSE_CORRECTION.code}.enabled": true,
         "hooks.host-execution-plan"                   : ExecutionPlan.getEndpointExecutionPlan(endpoint, [(ALL_PROCESSED_BID_RESPONSES): [PB_RESPONSE_CORRECTION]])]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getRichMediaFilterSettings(String scriptPattern,
                                                                    boolean filterMraidEnabled = true,
                                                                    Endpoint endpoint = OPENRTB2_AUCTION) {

        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : true,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled,
         "hooks.host-execution-plan"                                     : ExecutionPlan.getEndpointExecutionPlan(endpoint, [(ALL_PROCESSED_BID_RESPONSES): [PB_RICHMEDIA_FILTER]])]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getDisabledRichMediaFilterSettings(String scriptPattern,
                                                                            boolean filterMraidEnabled = true) {
        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : false,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getOrtb2BlockingSettings(boolean isEnabled = true) {
        ["hooks.${ORTB2_BLOCKING.code}.enabled": isEnabled as String]
    }
}
