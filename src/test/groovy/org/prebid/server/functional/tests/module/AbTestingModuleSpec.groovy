package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.ModuleName
import org.prebid.server.functional.model.config.AbTest
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.FetchStatus
import org.prebid.server.functional.model.request.auction.TraceLevel
import org.prebid.server.functional.model.response.auction.AnalyticResult
import org.prebid.server.functional.model.response.auction.InvocationResult
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.config.HookHttpEndpoint.AUCTION
import static org.prebid.server.functional.model.config.ModuleHookImplementation.ORTB2_BLOCKING_BIDDER_REQUEST
import static org.prebid.server.functional.model.config.ModuleHookImplementation.ORTB2_BLOCKING_RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.config.ModuleHookImplementation.RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.config.Stage.BIDDER_REQUEST
import static org.prebid.server.functional.model.config.Stage.RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultBidRequest
import static org.prebid.server.functional.model.response.auction.InvocationStatus.INVOCATION_FAILURE
import static org.prebid.server.functional.model.response.auction.InvocationStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.ModuleActivityName.AB_TESTING
import static org.prebid.server.functional.model.response.auction.ModuleActivityName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.response.auction.ResponseAction.NO_ACTION
import static org.prebid.server.functional.model.response.auction.ResponseAction.NO_INVOCATION

class AbTestingModuleSpec extends ModuleBaseSpec {

    private final static String NO_INVOCATION_METRIC = "modules.module.%s.stage.%s.hook.%s.success.no-invocation"
    private final static String CALL_METRIC = "modules.module.%s.stage.%s.hook.%s.call"
    private final static String EXECUTION_ERROR_METRIC = "modules.module.%s.stage.%s.hook.%s.execution-error"
    private final static Integer MIN_PERCENT_AB = 0
    private final static Integer MAX_PERCENT_AB = 100
    private final static String INVALID_HOOK_MESSAGE = "Hook implementation does not exist or disabled"

    private final static Map<Stage, List<ModuleName>> ORTB_STAGES = [(BIDDER_REQUEST)     : [ModuleName.ORTB2_BLOCKING],
                                                                     (RAW_BIDDER_RESPONSE): [ModuleName.ORTB2_BLOCKING]]
    private final static Map<Stage, List<ModuleName>> RESPONSE_STAGES = [(ALL_PROCESSED_BID_RESPONSES): [PB_RESPONSE_CORRECTION]]
    private final static Map<Stage, List<ModuleName>> MODULES_STAGES = ORTB_STAGES + RESPONSE_STAGES

    private final static Map<String, String> MULTI_MODULE_CONFIG = getResponseCorrectionConfig() + getOrtb2BlockingSettings() +
            ['hooks.host-execution-plan': null]

    private static final PrebidServerService ortbModulePbsService = pbsServiceFactory.getService(getOrtb2BlockingSettings())
    private static final PrebidServerService pbsServiceWithMultipleModules = pbsServiceFactory.getService(MULTI_MODULE_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(getOrtb2BlockingSettings())
        pbsServiceFactory.removeContainer(MULTI_MODULE_CONFIG)
    }

    def "PBS shouldn't apply a/b test config when config of ab test is disabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def abTest = AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
            enabled = false
        }
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            it.abTests = [abTest]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
        }

        and: "Shouldn't include any analytics tags"
        assert (invocationResults.analyticsTags.activities.flatten() as List<AnalyticResult>).findAll { it.name != AB_TESTING.value }

        and: "Metric for specified module should be as default call"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
    }

    def "PBS shouldn't apply valid a/b test config when module is disabled"() {
        given: "PBS service with disabled module config"
        def pbsConfig = getOrtb2BlockingSettings(false)
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code)]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [INVOCATION_FAILURE, INVOCATION_FAILURE]
            it.action == [null, null]
            it.analyticsTags == [null, null]
            it.message == [INVALID_HOOK_MESSAGE, INVALID_HOOK_MESSAGE]
        }

        and: "Metric for specified module should be with error call"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[EXECUTION_ERROR_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[EXECUTION_ERROR_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't apply a/b test config when module name is not matched"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(moduleName)]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
        }

        and: "Shouldn't include any analytics tags"
        assert (invocationResults.analyticsTags.activities.flatten() as List<AnalyticResult>).findAll { it.name != AB_TESTING.value }

        and: "Metric for specified module should be as default call"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        where:
        moduleName << [ModuleName.ORTB2_BLOCKING.code.toUpperCase(), PBSUtils.randomString]
    }

    def "PBS should apply a/b test config for each module when multiple config are presents and set to allow modules"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        and: "Save account with ab test config"
        def ortb2AbTestConfig = AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
            it.percentActive = MAX_PERCENT_AB
        }
        def richMediaAbTestConfig = AbTest.getDefault(PB_RESPONSE_CORRECTION.code).tap {
            it.percentActive = MAX_PERCENT_AB
        }
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [ortb2AbTestConfig, richMediaAbTestConfig]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.analyticsTags.activities.name.flatten().sort() == [ORTB2_BLOCKING, AB_TESTING, AB_TESTING].value.sort()
            it.analyticsTags.activities.status.flatten().sort() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS, FetchStatus.SUCCESS].sort()
            it.analyticsTags.activities.results.status.flatten().sort() == [FetchStatus.SUCCESS_ALLOW, FetchStatus.RUN, FetchStatus.RUN].sort()
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for other module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.RUN]
            it.analyticsTags.activities.results.values.module.flatten() == [PB_RESPONSE_CORRECTION]
        }

        and: "Metric for allowed to run ortb2blocking module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        and: "Metric for allowed to run response-correction module should be updated based on ab test config"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
    }

    def "PBS should apply a/b test config for each module when multiple config are presents and set to skip modules"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        and: "Save account with ab test config"
        def ortb2AbTestConfig = AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
            it.percentActive = MIN_PERCENT_AB
        }
        def richMediaAbTestConfig = AbTest.getDefault(PB_RESPONSE_CORRECTION.code).tap {
            it.percentActive = MIN_PERCENT_AB
        }
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [ortb2AbTestConfig, richMediaAbTestConfig]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for ortb2blocking module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should apply ab test config for response-correction module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [PB_RESPONSE_CORRECTION]
        }

        and: "Metric for skipped ortb2blocking module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        and: "Metric for skipped response-correction module should be updated based on ab test config"
        assert !metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
    }

    def "PBS should apply a/b test config for each module when multiple config are presents with different percentage"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        and: "Save account with ab test config"
        def ortb2AbTestConfig = AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
            it.percentActive = MIN_PERCENT_AB
        }
        def richMediaAbTestConfig = AbTest.getDefault(PB_RESPONSE_CORRECTION.code).tap {
            it.percentActive = MAX_PERCENT_AB
        }
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [ortb2AbTestConfig, richMediaAbTestConfig]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for ortb2blocking module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for response-correction module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.RUN]
            it.analyticsTags.activities.results.values.module.flatten() == [PB_RESPONSE_CORRECTION]
        }

        and: "Metric for skipped ortb2blocking module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "Metric for allowed to run response-correction module should be updated based on ab test config"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
    }

    def "PBS should ignore accounts property for a/b test config when ab test config specialize for specific account"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code, [PBSUtils.randomNumber]).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
    }

    def "PBS should apply a/b test config and run module when config is on max percentage or default value"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                it.percentActive = percentActive
                it.percentActiveSnakeCase = percentActiveSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for ortb module and run module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.analyticsTags.activities.name.flatten().sort() == [ORTB2_BLOCKING, AB_TESTING, AB_TESTING].value.sort()
            it.analyticsTags.activities.status.flatten().sort() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS, FetchStatus.SUCCESS].sort()
            it.analyticsTags.activities.results.status.flatten().sort() == [FetchStatus.SUCCESS_ALLOW, FetchStatus.RUN, FetchStatus.RUN].sort()
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be as default call"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        where:
        percentActive  | percentActiveSnakeCase
        MAX_PERCENT_AB | null
        null           | MAX_PERCENT_AB
        null           | null
    }

    def "PBS should apply a/b test config and skip module when config is on min percentage"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                it.percentActive = percentActive
                it.percentActiveSnakeCase = percentActiveSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for ortb module and skip this module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        where:
        percentActive  | percentActiveSnakeCase
        MIN_PERCENT_AB | null
        null           | MIN_PERCENT_AB
    }

    def "PBS shouldn't apply a/b test config without warnings and errors when percent config is out of lover range"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                it.percentActive = percentActive
                it.percentActiveSnakeCase = percentActiveSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        where:
        percentActive                 | percentActiveSnakeCase
        PBSUtils.randomNegativeNumber | null
        null                          | PBSUtils.randomNegativeNumber
    }

    def "PBS should apply a/b test config and run module without warnings and errors when percent config is out of appear range"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                it.percentActive = percentActive
                it.percentActiveSnakeCase = percentActiveSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "No error or warning should be emitted"
        assert !response.ext.errors
        assert !response.ext.warnings

        and: "PBS should apply ab test config for ortb module and run it"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]

            it.analyticsTags.activities.name.flatten().sort() == [ORTB2_BLOCKING, AB_TESTING, AB_TESTING].value.sort()
            it.analyticsTags.activities.status.flatten().sort() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS, FetchStatus.SUCCESS].sort()
            it.analyticsTags.activities.results.status.flatten().sort() == [FetchStatus.SUCCESS_ALLOW, FetchStatus.RUN, FetchStatus.RUN].sort()
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be as default call"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        where:
        percentActive                            | percentActiveSnakeCase
        PBSUtils.getRandomNumber(MAX_PERCENT_AB) | null
        null                                     | PBSUtils.getRandomNumber(MAX_PERCENT_AB)
    }

    def "PBS should include analytics tags when a/b test config when logAnalyticsTag is enabled or empty"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = MIN_PERCENT_AB
                it.logAnalyticsTag = logAnalyticsTag
                it.logAnalyticsTagSnakeCase = logAnalyticsTagSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module without analytics tags"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        where:
        logAnalyticsTag | logAnalyticsTagSnakeCase
        true            | null
        null            | true
        null            | null
    }

    def "PBS shouldn't include analytics tags when a/b test config when logAnalyticsTag is disabled and is applied by percentage"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = MIN_PERCENT_AB
                it.logAnalyticsTag = logAnalyticsTag
                it.logAnalyticsTagSnakeCase = logAnalyticsTagSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
        }

        and: "Shouldn't include any analytics tags"
        assert !invocationResults?.analyticsTags?.any()

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        where:
        logAnalyticsTag | logAnalyticsTagSnakeCase
        false           | null
        null            | false
    }

    def "PBS shouldn't include analytics tags when a/b test config when logAnalyticsTag is disabled and is non-applied by percentage"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = MAX_PERCENT_AB
                it.logAnalyticsTag = logAnalyticsTag
                it.logAnalyticsTagSnakeCase = logAnalyticsTagSnakeCase
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
        }

        and: "Shouldn't include any analytics tags"
        assert (invocationResults.analyticsTags.activities.flatten() as List<AnalyticResult>).findAll { it.name != AB_TESTING.value }

        and: "Metric for specified module should be as default call"
        def metrics = ortbModulePbsService.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        where:
        logAnalyticsTag | logAnalyticsTagSnakeCase
        false           | null
        null            | false
    }

    def "PBS shouldn't apply analytics tags for all module stages when module contain multiple stages"() {
        given: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(ortbModulePbsService)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, ORTB_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = PBSUtils.getRandomNumber(MIN_PERCENT_AB, MAX_PERCENT_AB)
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = ortbModulePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for all stages of specified module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll(invocationResults) {
            it.status.every { status -> status == it.status.first() }
            it.action.every { action -> action == it.action.first() }
        }

        and: "All resonances have same analytics"
        def abTestingInvocationResults = (invocationResults.analyticsTags.activities.flatten() as List<AnalyticResult>).findAll { it.name == AB_TESTING.value }
        verifyAll(abTestingInvocationResults) {
            it.status.flatten().every { status -> status == it.status.flatten().first() }
            it.results.status.flatten().every { status -> status == it.results.status.flatten().first() }
            it.results.values.module.flatten().every { module -> module == it.results.values.module.flatten().first() }
        }
    }

    def "PBS should apply a/b test config from host config when accounts is not specified when account config and default account doesn't include a/b test config"() {
        given: "PBS service with specific ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code, accouns).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ['hooks.host-execution-plan': encode(executionPlan)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for other module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]

            it.analyticsTags.every { it == null }
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "Metric for non specified module should be as default call"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        accouns << [null, []]
    }

    def "PBS should apply a/b test config from host config for specific accounts and only specified module when account config and default account doesn't include a/b test config"() {
        given: "PBS service with specific ab test config"
        def accountId = PBSUtils.randomNumber
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code, [PBSUtils.randomNumber, accountId]).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ['hooks.host-execution-plan': encode(executionPlan)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
            setAccountId(accountId as String)
        }

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for other module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]

            it.analyticsTags.every { it == null }
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "Metric for non specified module should be as default call"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should apply a/b test config from host config for specific account and general config when account config and default account doesn't include a/b test config"() {
        given: "PBS service with specific ab test config"
        def accountId = PBSUtils.randomNumber
        def ortb2AbTestConfig = AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code, []).tap {
            it.percentActive = MIN_PERCENT_AB
        }
        def richMediaAbTestConfig = AbTest.getDefault(PB_RESPONSE_CORRECTION.code, [accountId]).tap {
            it.percentActive = MIN_PERCENT_AB
        }
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [ortb2AbTestConfig, richMediaAbTestConfig]
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ['hooks.host-execution-plan': encode(executionPlan)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
            setAccountId(accountId as String)
        }

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for ortb2blocking module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should apply ab test config for response-correction module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [PB_RESPONSE_CORRECTION]
        }

        and: "Metric for skipped ortb2blocking module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert !metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        and: "Metric for skipped response-correction module should be updated based on ab test config"
        assert !metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS shouldn't apply a/b test config from host config for non specified accounts when account config and default account doesn't include a/b test config"() {
        given: "PBS service with specific ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code, [PBSUtils.randomNumber]).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ['hooks.host-execution-plan': encode(executionPlan)]
        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]

            it.analyticsTags.activities.name.flatten() == [ORTB2_BLOCKING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SUCCESS_ALLOW]
            it.analyticsTags.activities.results.values.module.flatten().every { it == null }
        }

        and: "PBS should not apply ab test config for other module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]

            it.analyticsTags.every { it == null }
        }

        and: "Metric for specified module should be as default call"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[CALL_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)]

        and: "Metric for non specified module should be as default call"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should prioritise a/b test config from default account and only specified module when host and default account contains a/b test configs"() {
        given: "PBS service with specific ab test config"
        def accountExecutionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(executionPlan: accountExecutionPlan)
        }

        def hostExecutionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code)]
        }
        def pbsConfig = MULTI_MODULE_CONFIG + ['hooks.host-execution-plan': encode(hostExecutionPlan)] + ["settings.default-account-config": encode(defaultAccountConfigSettings)]

        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS should apply ab test config for specified module and call it based on all execution plans"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS, SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION, NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING, AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS, FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED, FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for other modules and call them based on all execution plans"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]

            it.analyticsTags.every { it == null }
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 2
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 2

        and: "Metric for non specified module should be as default call"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 2
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 2

        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should prioritise a/b test config from account over default account and only specified module when specific account and default account contains a/b test configs"() {
        given: "PBS service with specific ab test config"
        def accountExecutionPlan = new ExecutionPlan(abTests: [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code)])
        def defaultAccountConfigSettings = AccountConfig.defaultAccountConfig.tap {
            hooks = new AccountHooksConfiguration(executionPlan: accountExecutionPlan)
        }

        def pbsConfig = MULTI_MODULE_CONFIG + ["settings.default-account-config": encode(defaultAccountConfigSettings)]

        def pbsServiceWithMultipleModules = pbsServiceFactory.getService(pbsConfig)

        and: "Default bid request with verbose trace"
        def bidRequest = getBidRequestWithTrace()

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        and: "Save account with ab test config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(AUCTION, MODULES_STAGES).tap {
            abTests = [AbTest.getDefault(ModuleName.ORTB2_BLOCKING.code).tap {
                percentActive = MIN_PERCENT_AB
            }]
        }
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(executionPlan: executionPlan))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        def invocationResults = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>

        and: "PBS should apply ab test config for specified module"
        def ortbBlockingInvocationResults = filterInvocationResultsByModule(invocationResults, ModuleName.ORTB2_BLOCKING)
        verifyAll(ortbBlockingInvocationResults) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_INVOCATION, NO_INVOCATION]
            it.analyticsTags.activities.name.flatten() == [AB_TESTING, AB_TESTING].value
            it.analyticsTags.activities.status.flatten() == [FetchStatus.SUCCESS, FetchStatus.SUCCESS]
            it.analyticsTags.activities.results.status.flatten() == [FetchStatus.SKIPPED, FetchStatus.SKIPPED]
            it.analyticsTags.activities.results.values.module.flatten() == [ModuleName.ORTB2_BLOCKING, ModuleName.ORTB2_BLOCKING]
        }

        and: "PBS should not apply ab test config for other module"
        def responseCorrectionInvocationResults = filterInvocationResultsByModule(invocationResults, PB_RESPONSE_CORRECTION)
        verifyAll(responseCorrectionInvocationResults) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]

            it.analyticsTags.every { it == null }
        }

        and: "Metric for specified module should be updated based on ab test config"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, BIDDER_REQUEST.metricValue, ORTB2_BLOCKING_BIDDER_REQUEST.code)] == 1
        assert metrics[NO_INVOCATION_METRIC.formatted(ModuleName.ORTB2_BLOCKING.code, RAW_BIDDER_RESPONSE.metricValue, ORTB2_BLOCKING_RAW_BIDDER_RESPONSE.code)] == 1

        and: "Metric for non specified module should be as default call"
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1
        assert metrics[CALL_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)] == 1

        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]
        assert !metrics[NO_INVOCATION_METRIC.formatted(PB_RESPONSE_CORRECTION.code, ALL_PROCESSED_BID_RESPONSES.metricValue, RESPONSE_CORRECTION_ALL_PROCESSED_RESPONSES.code)]

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    private static List<InvocationResult> filterInvocationResultsByModule(List<InvocationResult> invocationResults, ModuleName moduleName) {
        invocationResults.findAll { it.hookId.moduleCode == moduleName.code }
    }

    private static BidRequest getBidRequestWithTrace() {
        defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }
    }
}
