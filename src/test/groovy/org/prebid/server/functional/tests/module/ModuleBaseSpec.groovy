package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.HookHttpEndpoint
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.response.auction.AnalyticResult
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.IX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.config.HookHttpEndpoint.AUCTION
import static org.prebid.server.functional.model.config.ModuleName.OPTABLE_TARGETING
import static org.prebid.server.functional.model.config.ModuleName.PB_ORTB2_BLOCKING
import static org.prebid.server.functional.model.config.ModuleName.PB_REQUEST_CORRECTION
import static org.prebid.server.functional.model.config.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.config.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class ModuleBaseSpec extends BaseSpec {

    protected static final String WILDCARD = '*'
    protected static final String RANDOM_DATACENTER_REGION = PBSUtils.randomString
    protected static final Integer OPENX_VENDOR_ID = PBSUtils.getRandomNumber(0, 65534)
    protected static final Integer AMX_VENDOR_ID = PBSUtils.getRandomNumber(0, 65534)

    protected static final Map<String, String> GENERIC_CONFIG = [
            ("adapters.${GENERIC.value}.usersync.redirect.url".toString())         : "$networkServiceContainer.rootUri/generic-usersync".toString(),
            ("adapters.${GENERIC.value}.usersync.redirect.support-cors".toString()): 'false',
            ("adapters.${GENERIC.value}.meta-info.vendor-id".toString())           : GENERIC_VENDOR_ID.toString()]

    protected static final Map<String, String> IX_CONFIG = getAdapterConfig(IX)
    protected static final Map<String, String> OPENX_CONFIG = getAdapterConfig(OPENX, OPENX_VENDOR_ID)
    protected static final Map<String, String> OPENX_ALIAS_CONFIG = getAdapterConfig(OPENX, OPENX_ALIAS, OPENX_VENDOR_ID)
    protected static final Map<String, String> AMX_CONFIG = getAdapterConfig(AMX, AMX_VENDOR_ID)

    private static final Map<String, String> ENABLED_DEBUG_LOG_MODE = ["logging.level.root": "debug"]

    private static final Map<String, String> ORTB_ADAPTER_CONFIG = ['adapter-defaults.ortb.multiformat-supported': 'false']

    private static final Map<String, String> MODIFYING_VAST_CONFIG = ["adapter-defaults.modifying-vast-xml-allowed": "false",
                                                                      "adapters.generic.modifying-vast-xml-allowed": "false"]

    private static final Map<String, String> CACHE_STORAGE_CONFIG = ['storage.pbc.path'           : 'stored-cache',
                                                                     'storage.pbc.call-timeout-ms': '1000',
                                                                     'storage.pbc.enabled'        : 'true',
                                                                     'cache.module.enabled'       : 'true',
                                                                     'pbc.api.key'                : PBSUtils.randomString,
                                                                     'cache.api-key-secured'      : 'false']

    protected final static Map<String, String> EXTERNAL_MODULES_CONFIG = getModuleBaseSettings(PB_RICHMEDIA_FILTER) +
            getModuleBaseSettings(PB_RESPONSE_CORRECTION) +
            getModuleBaseSettings(PB_ORTB2_BLOCKING) +
            getModuleBaseSettings(PB_REQUEST_CORRECTION) +
            getOptableTargetingSettings() +
            getRulesEngineSettings()

    protected static PrebidServerService pbsServiceWithMultipleModules

    def setupSpec() {
        prebidCache.setResponse()
        bidder.setResponse()
        pbsServiceWithMultipleModules = pbsServiceFactory.getService(getDefaultMultipleModulesConfig())
    }

    def cleanupSpec() {
        bidder.reset()
        prebidCache.reset()
        repository.removeAllDatabaseData()
    }

    protected final static Closure<String> CALL_METRIC = { ModuleName module, Stage stage ->
        "modules.module.${module.code}.stage.${stage.metricValue}.hook.${module.code}-${stage.value}-hook.call"
    }
    protected final static Closure<String> UPDATE_METRIC = { ModuleName module, Stage stage ->
        "modules.module.${module.code}.stage.${stage.metricValue}.hook.${module.code}-${stage.value}-hook.success.update"
    }
    protected final static Closure<String> NOOP_METRIC = { ModuleName module, Stage stage ->
        "modules.module.${module.code}.stage.${stage.metricValue}.hook.${module.code}-${stage.value}-hook.success.noop"
    }
    protected final static Closure<String> NO_INVOCATION_METRIC = { ModuleName module, Stage stage ->
        "modules.module.${module.code}.stage.${stage.metricValue}.hook.${module.code}-${stage.value}-hook.success.no-invocation"
    }
    protected final static Closure<String> EXECUTION_ERROR_METRIC = { ModuleName module, Stage stage ->
        "modules.module.${module.code}.stage.${stage.metricValue}.hook.${module.code}-${stage.value}-hook.execution-error"
    }

    protected Map<String, String> getDefaultMultipleModulesConfig() {
        ['datacenter-region': RANDOM_DATACENTER_REGION] +
                EMPTY_CACHE_TTL_CONFIG +
                EXTERNAL_MODULES_CONFIG +
                ENABLED_DEBUG_LOG_MODE +
                ORTB_ADAPTER_CONFIG +
                MODIFYING_VAST_CONFIG +
                CACHE_STORAGE_CONFIG +
                GENERIC_CONFIG +
                IX_CONFIG +
                AMX_CONFIG +
                OPENX_CONFIG +
                OPENX_ALIAS_CONFIG
    }

    protected static Map<String, String> getModuleBaseSettings(ModuleName name, boolean enabled = true) {
        [("hooks.${name.code}.enabled".toString()): enabled.toString()]
    }

    protected static Map<String, String> getRichMediaFilterSettings(String scriptPattern,
                                                                    boolean filterMraidEnabled = true) {
        [
                "hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : true,
                "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
                "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled,
        ].collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getOptableTargetingSettings(boolean isEnabled = true) {
        [
                "hooks.${OPTABLE_TARGETING.code}.enabled"             : isEnabled.toString(),
                "hooks.modules.${OPTABLE_TARGETING.code}.api-endpoint": "${networkServiceContainer.rootUri}/stored-cache",
                "hooks.modules.${OPTABLE_TARGETING.code}.tenant"      : PBSUtils.randomString,
                "hooks.modules.${OPTABLE_TARGETING.code}.origin"      : PBSUtils.randomString
        ].collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Map<String, String> getRulesEngineSettings() {
        [
                "hooks.${PB_RULE_ENGINE.code}.enabled"                                : "true",
                "hooks.${PB_RULE_ENGINE.code}.rule-cache.expire-after-minutes"        : "10000",
                "hooks.${PB_RULE_ENGINE.code}.rule-cache.max-size"                    : "20000",
                "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-initial-delay-millis": "10000",
                "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-max-delay-millis"    : "10000",
                "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-exponential-factor"  : "1.2",
                "hooks.${PB_RULE_ENGINE.code}.rule-parsing.retry-exponential-jitter"  : "1.2"
        ].collectEntries { key, value -> [(key.toString()): value.toString()] }
    }

    protected static Account getAccountWithModuleConfig(String accountId,
                                                        Map<Stage, List<ModuleName>> modulesStages,
                                                        HookHttpEndpoint endpoint = AUCTION) {

        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(endpoint, modulesStages)
        def accountHooksConfig = new AccountHooksConfiguration(executionPlan: executionPlan, modules: new PbsModulesConfig())
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        new Account(uuid: accountId, config: accountConfig)
    }

    protected static List<AnalyticResult> getAnalyticResults(BidResponse response) {
        response.ext.prebid.modules?.trace?.stages?.first()
                ?.outcomes?.first()?.groups?.first()
                ?.invocationResults?.first()?.analyticsTags?.activities
    }

    private static Map<String, String> getAdapterConfig(BidderName bidder, Integer vendorId = null) {
        ([
                "adapters.${bidder.value}.enabled": 'true',
                "adapters.${bidder.value}.endpoint": "${networkServiceContainer.rootUri}/auction"
        ] + (vendorId != null ? ["adapters.${bidder.value}.meta-info.vendor-id": vendorId] : [:]))
                .collectEntries { k, v -> [(k.toString()): v.toString()] }
    }

    private static Map<String, String> getAdapterConfig(BidderName bidder, BidderName alias, Integer vendorId = null) {
        ([
                "adapters.${bidder.value}.aliases.${alias.value}.enabled": 'true',
                "adapters.${bidder.value}.aliases.${alias.value}.endpoint": "${networkServiceContainer.rootUri}/auction"
        ] + (vendorId != null ? ["adapters.${bidder.value}.aliases.${alias.value}.meta-info.vendor-id": vendorId] : [:]))
                .collectEntries { k, v -> [(k.toString()): v.toString()] }
    }
}
