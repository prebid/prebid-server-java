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
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpUnitCode
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.config.ModuleHookImplementation.PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.model.config.Stage.PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.GPID
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.PB_AD_SLOT
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.STORED_REQUEST
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.TAG_ID
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

abstract class RuleEngineBaseSpec extends ModuleBaseSpec {

    protected static final List<BidderName> MULTI_BID_ADAPTERS = [GENERIC, OPENX, AMX].sort()
    protected static final String APPLIED_FOR_ALL_IMPS = "*"
    protected static final String DEFAULT_CONDITIONS = "default"
    protected final static String CALL_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.call"
    protected final static String NOOP_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.success.noop"
    protected final static String UPDATE_METRIC = "modules.module.${PB_RULE_ENGINE.code}.stage.${PROCESSED_AUCTION_REQUEST.metricValue}.hook.${PB_RULES_ENGINE_PROCESSED_AUCTION_REQUEST.code}.success.update"
    protected final static Closure<String> INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING = { accountId, functionType ->
        "Failed to parse rule-engine config for account $accountId: " +
                "Function '$functionType' configuration is invalid: " +
                "Field '$functionType.fieldName' is required and has to be an array of strings"
    }

    protected final static Closure<String> INVALID_CONFIGURATION_FOR_SINGLE_STRING_LOG_WARNING = { accountId, functionType ->
        "Failed to parse rule-engine config for account $accountId: " +
                "Function '$functionType' configuration is invalid: " +
                "Field '$functionType.fieldName' is required and has to be a string"
    }

    protected final static Closure<String> INVALID_CONFIGURATION_FOR_SINGLE_INTEGER_LOG_WARNING = { accountId, functionType ->
        "Failed to parse rule-engine config for account $accountId: " +
                "Function '$functionType' configuration is invalid: " +
                "Field '$functionType.fieldName' is required and has to be an integer"
    }

    protected final static Closure<String> INVALID_CONFIGURATION_FOR_INTEGERS_LOG_WARNING = { accountId, functionType ->
        "Failed to parse rule-engine config for account $accountId: " +
                "Function '$functionType' configuration is invalid: " +
                "Field '$functionType.fieldName' is required and has to be an array of integers"
    }

    protected static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX}.enabled" : "true",
                                                               "adapters.${OPENX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final Map<String, String> AMX_CONFIG = ["adapters.${AMX}.enabled" : "true",
                                                             "adapters.${AMX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final Map<String, String> OPENX_ALIAS_CONFIG = ["adapters.${OPENX}.aliases.${OPENX_ALIAS}.enabled" : "true",
                                                                     "adapters.${OPENX}.aliases.${OPENX_ALIAS}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    protected static final String CONFIG_DATA_CENTER = PBSUtils.randomString
    private static final String USER_SYNC_URL = "$networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.redirect.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.redirect.support-cors": false as String,
            "adapters.${GENERIC.value}.meta-info.vendor-id"           : GENERIC_VENDOR_ID as String]
    protected static final PrebidServerService pbsServiceWithRulesEngineModule = pbsServiceFactory.getService(GENERIC_CONFIG +
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

    protected static void updateBidderImp(Imp imp, List<BidderName> bidders = MULTI_BID_ADAPTERS) {
        imp.ext.prebid.bidder.tap {
            openx = bidders.contains(OPENX) ? Openx.defaultOpenx : null
            openxAlias = bidders.contains(OPENX_ALIAS) ? Openx.defaultOpenx : null
            amx = bidders.contains(AMX) ? new Amx() : null
            generic = bidders.contains(GENERIC) ? new Generic() : null
            alias = bidders.contains(ALIAS) ? new Generic() : null
        }
    }

    protected static void updateBidRequestWithGeoCountry(BidRequest bidRequest, Country country = USA) {
        bidRequest.device = new Device(geo: new Geo(country: country))
    }

    protected static getAccountWithRulesEngine(String accountId, PbRulesEngine ruleEngine) {
        def accountHooksConfiguration = new AccountHooksConfiguration(modules: new PbsModulesConfig(pbRuleEngine: ruleEngine))
        new Account(uuid: accountId, config: new AccountConfig(hooks: accountHooksConfiguration))
    }

    protected static BidRequest createBidRequestWithDomains(DistributionChannel type, String domain, boolean usePublisher = true) {
        def request = getDefaultBidRequestWithMultiplyBidders(type)

        switch (type) {
            case SITE:
                if (usePublisher) request.site.publisher.domain = domain
                else request.site.domain = domain
                break
            case APP:
                if (usePublisher) request.app.publisher.domain = domain
                else request.app.domain = domain
                break
            case DOOH:
                if (usePublisher) request.dooh.publisher.domain = domain
                else request.dooh.domain = domain
                break
        }
        request
    }

    protected static BidRequest updatePublisherDomain(BidRequest bidRequest, DistributionChannel distributionChannel, String domain) {
        if (distributionChannel == SITE) {
            bidRequest.tap {
                it.site.publisher.domain = domain
            }
        }
        if (distributionChannel == APP) {
            bidRequest.tap {
                it.app.publisher.domain = domain
            }
        }
        if (distributionChannel == DOOH) {
            bidRequest.tap {
                it.dooh.publisher.domain = domain
            }
        }
    }

    protected static String getImpAdUnitCodeByCode(Imp imp, ImpUnitCode impUnitCode) {
        if (TAG_ID == impUnitCode) {
            return imp.tagId
        }
        if (GPID == impUnitCode) {
            return imp.ext.gpid
        }
        if (PB_AD_SLOT == impUnitCode) {
            return imp.ext.data.pbAdSlot
        }
        if (STORED_REQUEST == impUnitCode) {
            return imp.ext.prebid.storedRequest.id
        }
        return null
    }

    protected static String getImpAdUnitCode(Imp imp) {
        if (imp.ext.gpid) {
            return imp.ext.gpid
        }
        if (imp.tagId) {
            return imp.tagId
        }
        if (imp.ext.data.pbAdSlot) {
            return imp.ext.data.pbAdSlot
        }
        if (imp.ext.prebid.storedRequest.id) {
            return imp.ext.prebid.storedRequest.id
        }
        return null
    }
}
