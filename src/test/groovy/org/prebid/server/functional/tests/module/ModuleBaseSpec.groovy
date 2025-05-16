package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.Endpoint
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.ModuleHookImplementation
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.BaseSpec
import spock.lang.Shared

import static org.prebid.server.functional.model.config.ModuleHookImplementation.PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES
import static org.prebid.server.functional.model.config.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.config.ModuleName.PB_REQUEST_CORRECTION
import static org.prebid.server.functional.model.config.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.config.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class ModuleBaseSpec extends BaseSpec {

    protected static final String WILDCARD = '*'
    private static final Map<String, String> ORTB_ADAPTER_CONFIG = ['adapter-defaults.ortb.multiformat-supported': 'false']
    private static final Map<String, String> MODIFYING_VAST_CONFIG = ["adapter-defaults.modifying-vast-xml-allowed": "false",
                                                                      "adapters.generic.modifying-vast-xml-allowed": "false"]
    private static final Map IX_CONFIG = ["adapters.ix.enabled" : "true",
                                          "adapters.ix.endpoint": "$networkServiceContainer.rootUri/auction".toString()]

    protected final static Map<String, String> EXTERNAL_MODULES_CONFIG = getModuleBaseSettings(PB_RICHMEDIA_FILTER) +
            getModuleBaseSettings(PB_RESPONSE_CORRECTION) +
            getModuleBaseSettings(ORTB2_BLOCKING) +
            getModuleBaseSettings(PB_REQUEST_CORRECTION)

    @Shared
    protected static PrebidServerService pbsServiceWithMultipleModules

    def setupSpec() {
        prebidCache.setResponse()
        bidder.setResponse()
        pbsServiceWithMultipleModules = pbsServiceFactory.getService(
                IX_CONFIG +
                        ORTB_ADAPTER_CONFIG +
                        MODIFYING_VAST_CONFIG +
                        EXTERNAL_MODULES_CONFIG
        )
    }

    def cleanupSpec() {
        bidder.reset()
        prebidCache.reset()
        repository.removeAllDatabaseData()
    }

    protected static Map<String, String> getModuleBaseSettings(ModuleName name, boolean isEnabled = true) {
        [("hooks.${name.code}.enabled".toString()): isEnabled as String]
    }

    protected static Map<String, String> getRichMediaFilterSettings(String scriptPattern,
                                                                    Boolean filterMraidEnabled = true) {
        ["hooks.${PB_RICHMEDIA_FILTER.code}.enabled"                     : true,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.mraid-script-pattern": scriptPattern,
         "hooks.modules.${PB_RICHMEDIA_FILTER.code}.filter-mraid"        : filterMraidEnabled,
         "hooks.host-execution-plan"                                     : encode(ExecutionPlan.getSingleEndpointExecutionPlan([PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES]))]
                .findAll { it.value != null }
                .collectEntries { key, value -> [(key.toString()): value.toString()]
                }
    }

    protected static Account getAccountWithModuleConfig(String accountId,
                                                        List<ModuleHookImplementation> modulesHooks,
                                                        Endpoint endpoint = OPENRTB2_AUCTION) {

        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(modulesHooks, endpoint)
        def accountHooksConfig = new AccountHooksConfiguration(executionPlan: executionPlan, modules: new PbsModulesConfig())
        def accountConfig = new AccountConfig(hooks: accountHooksConfig)
        new Account(uuid: accountId, config: accountConfig)
    }

}
