package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.PbRulesEngine
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.config.ModuleHookImplementation.PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.model.config.Stage.PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

abstract class RuleEngineBaseSpec extends ModuleBaseSpec {

    protected static final List<BidderName> MULTI_BID_ADAPTERS = [GENERIC, OPENX, AMX].sort()
    protected static final String APPLIED_FOR_ALL_IMPS = "*"
    protected static final String DEFAULT_CONDITIONS = "default"
    protected final static String CALL_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.call"
    protected final static String FAILER_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.failure"
    protected final static String NOOP_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.success.noop"
    protected final static String UPDATE_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.success.update"
    protected static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX}.enabled" : "true",
                                                               "adapters.${OPENX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final Map<String, String> AMX_CONFIG = ["adapters.${AMX}.enabled" : "true",
                                                             "adapters.${AMX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final Map<String, String> OPENX_ALIAS_CONFIG = ["adapters.${OPENX}.aliases.${OPENX_ALIAS}.enabled" : "true",
                                                                     "adapters.${OPENX}.aliases.${OPENX_ALIAS}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final String CONFIG_DATA_CENTER = PBSUtils.randomString
    protected static final PrebidServerService pbsServiceWithRulesEngineModule = pbsServiceFactory.getService(
            getRulesEngineSettings() + AMX_CONFIG + OPENX_CONFIG + OPENX_ALIAS_CONFIG + ['datacenter-region': CONFIG_DATA_CENTER])

    protected static BidRequest getDefaultBidRequestWithMultiplyBidders(DistributionChannel distributionChannel = SITE) {
        BidRequest.getDefaultBidRequest(distributionChannel).tap {
            it.imp[0].ext.prebid.bidder.amx = new Amx()
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.imp[0].ext.prebid.bidder.generic = new Generic()
            it.ext.prebid.trace = VERBOSE
            it.ext.prebid.returnAllBidStatus = true
        }
    }

    protected static void updateBidRequestWithGeoCountry(BidRequest bidRequest, Country country = USA) {
        bidRequest.device = new Device(geo: new Geo(country: country))
    }

    protected static getAccountWithRulesEngine(String accountId, PbRulesEngine ruleEngine) {
        def accountHooksConfiguration = new AccountHooksConfiguration(modules: new PbsModulesConfig(pbRuleEngine: ruleEngine))
        new Account(uuid: accountId, config: new AccountConfig(hooks: accountHooksConfiguration))
    }
}
