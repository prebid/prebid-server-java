package org.prebid.server.functional.tests.module

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.PbResponseCorrection
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
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

class GeneralModuleSpec extends ModuleBaseSpec {

    private final static Map<String, String> MULTY_MODULE_CONFIG = getRichMediaFilterSettings(PBSUtils.randomString) + getResponseCorrectionConfig() +
            ['hooks.host-execution-plan': encode(ExecutionPlan.getEndpointExecutionPlan(OPENRTB2_AUCTION, [(ALL_PROCESSED_BID_RESPONSES): [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION]]))]

    private final static PrebidServerService pbsServiceWithMultipleModule = pbsServiceFactory.getService(MULTY_MODULE_CONFIG)
    private final static PrebidServerService pbsServiceWithMultipleModuleWithRequireInvoke = pbsServiceFactory.getService(MULTY_MODULE_CONFIG + ['settings.modules.require-config-to-invoke': 'true'])

    def "PBS should call any modules when account.modules config empty and require-config-to-invoke disabled"() {
        given: "Default bid request"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: null))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModule.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll (response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS, SUCCESS]
            it.action == [NO_ACTION, NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RICHMEDIA_FILTER, PB_RESPONSE_CORRECTION].code.sort()
        }
    }

    def "PBS shouldn't call any modules when account.modules config empty and require-config-to-invoke enabled"() {
        given: "Default bid request"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: null))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't include trace information about calling modules"
        assert !response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults
    }

    def "PBS should call only specified module when account.modules config include that module and require-config-to-invoke enabled"() {
        given: "Default bid request"
        def bidRequest = defaultBidRequest.tap {
            ext.prebid.trace = TraceLevel.VERBOSE
        }

        and: "Save account with enabled response correction module"
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: new PbsModulesConfig(pbResponseCorrection: new PbResponseCorrection())))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModuleWithRequireInvoke.sendAuctionRequest(bidRequest)

        then: "PBS response should include trace information about called modules"
        verifyAll (response?.ext?.prebid?.modules?.trace?.stages?.outcomes?.groups?.invocationResults?.flatten() as List<InvocationResult>) {
            it.status == [SUCCESS]
            it.action == [NO_ACTION]
            it.hookId.moduleCode.sort() == [PB_RESPONSE_CORRECTION].code.sort()
        }
    }

}
