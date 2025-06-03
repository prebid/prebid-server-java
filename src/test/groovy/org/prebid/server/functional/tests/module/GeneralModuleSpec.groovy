package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.ModuleName
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.AdminConfig
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.Ortb2BlockingConfig
import org.prebid.server.functional.model.config.PbResponseCorrection
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.RichmediaFilter
import org.prebid.server.functional.model.request.auction.TraceLevel
import org.prebid.server.functional.model.response.auction.InvocationResult
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.ModuleHookImplementation.ORTB2_BLOCKING_BIDDER_REQUEST
import static org.prebid.server.functional.model.config.ModuleHookImplementation.ORTB2_BLOCKING_RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.config.ModuleHookImplementation.PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.config.Stage.BIDDER_REQUEST
import static org.prebid.server.functional.model.config.Stage.RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultBidRequest
import static org.prebid.server.functional.model.response.auction.InvocationStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.ResponseAction.NO_ACTION

class GeneralModuleSpec extends ModuleBaseSpec {

    private final static String CALL_METRIC = "modules.test.%s.stage.%s.hook.%s.call"
    private final static String NOOP_METRIC = "modules.module.%s.stage.%s.hook.%s.success.noop"

    private final static Map<String, String> DISABLED_INVOKE_CONFIG = ['settings.modules.require-config-to-invoke': 'false']
    private final static Map<String, String> ENABLED_INVOKE_CONFIG = ['settings.modules.require-config-to-invoke': 'true']

    private final static Map<Stage, List<ModuleName>> ORTB_STAGES = [(BIDDER_REQUEST)     : [ORTB2_BLOCKING],
                                                                     (RAW_BIDDER_RESPONSE): [ORTB2_BLOCKING]]
    private final static Map<Stage, List<ModuleName>> RESPONSE_STAGES = [(ALL_PROCESSED_BID_RESPONSES): [PB_RICHMEDIA_FILTER]]
    private final static Map<Stage, List<ModuleName>> MODULES_STAGES = ORTB_STAGES + RESPONSE_STAGES
    private final static Map<String, String> MULTI_MODULE_CONFIG = getRichMediaFilterSettings(PBSUtils.randomString) +
            getOrtb2BlockingSettings() +
            ['hooks.host-execution-plan': encode(ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, MODULES_STAGES))]

    private static final PrebidServerService pbsServiceWithMultipleModule = pbsServiceFactory.getService(MULTI_MODULE_CONFIG + DISABLED_INVOKE_CONFIG)
    private static final PrebidServerService pbsServiceWithMultipleModuleWithRequireInvoke = pbsServiceFactory.getService(MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(MULTI_MODULE_CONFIG + DISABLED_INVOKE_CONFIG)
        pbsServiceFactory.removeContainer(MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG)
    }

    def "PBS should call all modules and traces response when account config is empty and require-config-to-invoke is disabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModule.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [ORTB2_BLOCKING, ORTB2_BLOCKING, PB_RICHMEDIA_FILTER].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModule.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "RB-Richmedia-Filter module call metrics should be updated"
        assert metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1

        where:
        modulesConfig << [null, new PbsModulesConfig()]
    }

    def "PBS should call all modules and traces response when account includes modules config and require-config-to-invoke is disabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def pbsModulesConfig = new PbsModulesConfig(pbRichmediaFilter: pbRichmediaFilterConfig, pbResponseCorrection: pbResponseCorrectionConfig)
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: pbsModulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModule)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModule.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModule.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "RB-Richmedia-Filter module call metrics should be updated"
        assert metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1

        where:
        pbRichmediaFilterConfig                | pbResponseCorrectionConfig
        new RichmediaFilter()                  | new PbResponseCorrection()
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: false)
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: true)
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection()
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection(enabled: true)
    }

    def "PBS should call all modules and traces response when default-account includes modules config and require-config-to-invoke is enabled"() {
        given: "PBS service with  module config"
        def pbsModulesConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(), ortb2Blocking: new Ortb2BlockingConfig())
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(modules: pbsModulesConfig)
        }

        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "RB-Richmedia-Filter module call metrics should be updated"
        assert metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should call all modules and traces response when account includes modules config and require-config-to-invoke is enabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def pbsModulesConfig = new PbsModulesConfig(pbRichmediaFilter: pbRichmediaFilterConfig, ortb2Blocking: ortb2BlockingConfig)
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: pbsModulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModuleWithRequireInvoke)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModuleWithRequireInvoke.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "RB-Richmedia-Filter module call metrics should be updated"
        assert metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1

        where:
        pbRichmediaFilterConfig                | ortb2BlockingConfig
        new RichmediaFilter()                  | new Ortb2BlockingConfig()
        new RichmediaFilter()                  | new Ortb2BlockingConfig(attributes: [:] as Map)
        new RichmediaFilter()                  | new Ortb2BlockingConfig(attributes: [:] as Map)
        new RichmediaFilter(filterMraid: true) | new Ortb2BlockingConfig()
        new RichmediaFilter(filterMraid: true) | new Ortb2BlockingConfig(attributes: [:] as Map)
    }

    def "PBS should call specified module and traces response when account config includes that module and require-config-to-invoke is enabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter())))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModuleWithRequireInvoke)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called module"
        def invocationTrace = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationTrace.findAll { it -> it.hookId.moduleCode == PB_RICHMEDIA_FILTER.code }) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModuleWithRequireInvoke.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        and: "RB-Richmedia-Filter module call metrics should be updated"
        assert metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)] == 1
    }

    def "PBS shouldn't call any modules and traces that in response when account config is empty and require-config-to-invoke is enabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModuleWithRequireInvoke)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't include trace information about no-called modules"
        assert !response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten()

        and: "Ortb2blocking module call metrics shouldn't be updated"
        def metrics = pbsServiceWithMultipleModuleWithRequireInvoke.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        and: "RB-Richmedia-Filter module call metrics shouldn't be updated"
        assert !metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]

        where:
        modulesConfig << [null, new PbsModulesConfig()]
    }

    def "PBS should call all modules without account config when modules enabled in module-execution host config"() {
        given: "PBS service with module-execution config"
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG +
                [("hooks.admin.module-execution.${ORTB2_BLOCKING.code}".toString()): 'true']
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't call any module without account config when modules disabled in module-execution host config"() {
        given: "PBS service with module-execution config"
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG +
                [("hooks.admin.module-execution.${ORTB2_BLOCKING.code}".toString()): 'false']
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't include trace information about no-called modules"
        assert !response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten()

        and: "Ortb2blocking module call metrics shouldn't be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't call module and not override host config when default-account module-execution config enabled module"() {
        given: "PBS service with module-execution and default account configs"
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(admin: new AdminConfig(moduleExecution: [(ORTB2_BLOCKING): true]))
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)] +
        [("hooks.admin.module-execution.${ORTB2_BLOCKING.code}".toString()): 'false']
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: null))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't include trace information about no-called modules"
        assert !response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten()

        and: "Ortb2blocking module call metrics shouldn't be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should call module without account module config when default-account module-execution config enabling module"() {
        given: "PBS service with module-execution and default account configs"
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(admin: new AdminConfig(moduleExecution: [(ORTB2_BLOCKING): true]))
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: null))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't call any modules without account config when default-account module-execution config not enabling module"() {
        given: "PBS service with module-execution and default account configs"
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(admin: new AdminConfig(moduleExecution: [(ORTB2_BLOCKING): moduleExecutionStatus]))
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: null))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't include trace information about no-called modules"
        assert !response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten()

        and: "Ortb2blocking module call metrics shouldn't be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        and: "RB-Richmedia-Filter module call metrics shouldn't be updated"
        assert !metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        moduleExecutionStatus << [false, null]
    }

    def "PBS should prioritize specific account module-execution config over default-account module-execution config when both are present"() {
        given: "PBS service with default account config"
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(admin: new AdminConfig(moduleExecution: [(ORTB2_BLOCKING): false]))
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ENABLED_INVOKE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(admin: new AdminConfig(moduleExecution: [(ORTB2_BLOCKING): true])))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [ORTB2_BLOCKING, ORTB2_BLOCKING].code.sort()
        }

        and: "Ortb2blocking module call metrics should be updated"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NOOP_METRIC.formatted(ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "RB-Richmedia-Filter module call metrics shouldn't be updated"
        assert !metrics[CALL_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NOOP_METRIC.formatted(PB_RICHMEDIA_FILTER.code, ALL_PROCESSED_BID_RESPONSES.metricValue, PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }
}
