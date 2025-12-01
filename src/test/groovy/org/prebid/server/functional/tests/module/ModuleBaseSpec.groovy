package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.response.auction.AnalyticResult
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.InvocationResult
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.OPTABLE_TARGETING
import static org.prebid.server.functional.model.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.ModuleName.PB_REQUEST_CORRECTION
import static org.prebid.server.functional.model.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.config.Stage.PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

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
         "hooks.host-execution-plan"                   : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, [(ALL_PROCESSED_BID_RESPONSES): [PB_RESPONSE_CORRECTION]]))]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getRichMediaFilterSettings(String scriptPattern,
                                                                    Boolean filterMraidEnabled = true,
                                                                    Endpoint endpoint = OPENRTB2_AUCTION) {

        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : true,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled,
         "hooks.host-execution-plan"                                     : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, [(ALL_PROCESSED_BID_RESPONSES): [PB_RICHMEDIA_FILTER]]))]
                .findAll { it.value != null }
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getDisabledRichMediaFilterSettings(String scriptPattern,
                                                                            boolean filterMraidEnabled = true) {
        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : false,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getOptableTargetingSettings(boolean isEnabled = true, Endpoint endpoint = OPENRTB2_AUCTION) {
        ["hooks.${OPTABLE_TARGETING.code}.enabled": isEnabled as String,
         "hooks.modules.${OPTABLE_TARGETING.code}.api-endpoint" : "$networkServiceContainer.rootUri/stored-cache".toString(),
         "hooks.modules.${OPTABLE_TARGETING.code}.tenant" : PBSUtils.randomString,
         "hooks.modules.${OPTABLE_TARGETING.code}.origin" : PBSUtils.randomString,
         "hooks.host-execution-plan"              : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, [(PROCESSED_AUCTION_REQUEST): [OPTABLE_TARGETING]]))]
                .collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getOrtb2BlockingSettings(boolean isEnabled = true) {
        ["hooks.${ORTB2_BLOCKING.code}.enabled": isEnabled as String]
    }

    protected static Map<String, String> getRequestCorrectionSettings(Endpoint endpoint = OPENRTB2_AUCTION, Stage stage = PROCESSED_AUCTION_REQUEST) {
        ["hooks.${PB_REQUEST_CORRECTION.code}.enabled": "true",
         "hooks.host-execution-plan"                  : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, PB_REQUEST_CORRECTION, [stage]))]
    }

    protected static Map<String, String> getRulesEngineSettings(Endpoint endpoint = OPENRTB2_AUCTION, Stage stage = PROCESSED_AUCTION_REQUEST) {
        ["hooks.${PB_RULE_ENGINE.code}.enabled"                                : "true",
         "hooks.${PB_RULE_ENGINE.code}.rule-cache.expire-after-minutes"        : "10000",
         "hooks.${PB_RULE_ENGINE.code}.rule-cache.max-size"                    : "20000",
         "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-initial-delay-millis": "10000",
         "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-max-delay-millis"    : "10000",
         "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-exponential-factor"  : "1.2",
         "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-exponential-jitter"  : "1.2",
         "hooks.host-execution-plan"                                           : encode(ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, PB_RULE_ENGINE, [stage]))]
    }

    protected static List<AnalyticResult> getAnalyticResults(BidResponse response) {
        response.ext.prebid.modules?.trace?.stages?.first()
                ?.outcomes?.first()?.groups?.first()
                ?.invocationResults?.first()?.analyticsTags?.activities
    }
}
