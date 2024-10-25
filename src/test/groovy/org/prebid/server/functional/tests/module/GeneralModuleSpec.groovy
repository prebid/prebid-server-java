package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.PbResponseCorrection
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.RichmediaFilter
import org.prebid.server.functional.model.request.auction.TraceLevel
import org.prebid.server.functional.model.response.auction.InvocationResult
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.PB_RESPONSE_CORRECTION
import static org.prebid.server.functional.model.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.request.auction.BidRequest.getDefaultBidRequest
import static org.prebid.server.functional.model.response.auction.InvocationStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.ResponseAction.NO_ACTION
import static org.prebid.server.functional.model.response.auction.ResponseAction.NO_CALLED

class GeneralModuleSpec extends ModuleBaseSpec {

    private final static Map<String, String> ENABLED_INVOKE_CONFIG = ['settings.modules.require-config-to-invoke': 'true']
    private final static Map<String, String> MULTY_MODULE_CONFIG = getRichMediaFilterSettings(PBSUtils.randomString) + getResponseCorrectionConfig() +
            ['hooks.host-execution-plan': encode(ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, [(ALL_PROCESSED_BID_RESPONSES): [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION]]))]

    private final static PrebidServerService pbsServiceWithMultipleModule = pbsServiceFactory.getService(MULTY_MODULE_CONFIG)
    private final static PrebidServerService pbsServiceWithMultipleModuleWithRequireInvoke = pbsServiceFactory.getService(MULTY_MODULE_CONFIG + ENABLED_INVOKE_CONFIG)

    def "PBS should call all modules and traces response when account config is empty and require-config-to-invoke is disabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account without modules config"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: modulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModule.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION].code.sort()
        }

        where :
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

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModule.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION].code.sort()
        }

        where:
        pbRichmediaFilterConfig                | pbResponseCorrectionConfig
        new RichmediaFilter()                  | new PbResponseCorrection()
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: false)
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: true)
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection()
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection(enabled: true)
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

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about no-called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_CALLED, NO_CALLED]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION].code.sort()
        }

        where :
        modulesConfig << [null, new PbsModulesConfig()]
    }

    def "PBS should call all modules and traces response when account includes modules config and require-config-to-invoke is enabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def pbsModulesConfig = new PbsModulesConfig(pbRichmediaFilter: pbRichmediaFilterConfig, pbResponseCorrection: pbResponseCorrectionConfig)
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: pbsModulesConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION].code.sort()
        }

        where:
        pbRichmediaFilterConfig                | pbResponseCorrectionConfig
        new RichmediaFilter()                  | new PbResponseCorrection()
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: false)
        new RichmediaFilter()                  | new PbResponseCorrection(enabled: true)
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection()
        new RichmediaFilter(filterMraid: true) | new PbResponseCorrection(enabled: true)
    }

    def "PBS should call specified module and traces response when account config includes that module and require-config-to-invoke is enabled"() {
        given: "Default bid request with verbose trace"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: new PbsModulesConfig(pbResponseCorrection: new PbResponseCorrection())))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called module"
        def invocationTrace = response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>
        verifyAll (invocationTrace.findAll {it -> it.hookId.moduleCode == PB_RESPONSE_CORRECTION.code}) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RESPONSE_CORRECTION].code.sort()
        }

        and: "PBS response should include trace information about no-called module"
        verifyAll(response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS]
            it.action == [NO_CALLED]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER].code.sort()
        }
    }
}
