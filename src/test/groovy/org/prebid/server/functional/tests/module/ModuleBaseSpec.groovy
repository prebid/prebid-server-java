package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.tests.BaseSpec
import spock.lang.Retry

import static org.prebid.server.functional.model.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES

//@Retry(mode = Retry.Mode.SETUP_FEATURE_CLEANUP)
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

    protected static Map<String, String> getRichMediaFilterSettings(String scriptPattern,
                                                                    boolean filterMraidEnabled = true,
                                                                    Endpoint endpoint = OPENRTB2_AUCTION) {

        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : true,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled,
         "hooks.host-execution-plan"                                     : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, PB_RICHMEDIA_FILTER, ALL_PROCESSED_BID_RESPONSES))]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }

    }
}
