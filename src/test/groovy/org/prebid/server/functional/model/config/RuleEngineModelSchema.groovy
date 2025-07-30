package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DATA_CENTER_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_TYPE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.MEDIA_TYPE_IN
import static org.prebid.server.functional.model.pricefloors.Country.USA

@ToString(includeNames = true, ignoreNulls = true)
class RuleEngineModelSchema {

    RuleEngineFunction function
    RuleEngineFunctionArgs args

    static RuleEngineModelSchema createDeviceCountryInSchema(List<Object> argsCountries = [USA]) {
        new RuleEngineModelSchema().tap {
            it.function = DEVICE_COUNTRY_IN
            it.args = new RuleEngineFunctionArgs(countries: argsCountries)
        }
    }

    static RuleEngineModelSchema createDataCenterInSchema(List<String> argsDataCenter = [PBSUtils.randomString]) {
        new RuleEngineModelSchema().tap {
            it.function = DATA_CENTER_IN
            it.args = new RuleEngineFunctionArgs(datacenters: argsDataCenter)
        }
    }

    static RuleEngineModelSchema createEidInSchema(List<String> argsEid = [PBSUtils.randomString]) {
        new RuleEngineModelSchema().tap {
            it.function = EID_IN
            it.args = new RuleEngineFunctionArgs(sources: argsEid)
        }
    }

    static RuleEngineModelSchema createGppSidInSchema(List<GppSectionId> argsGppSid = [PBSUtils.getRandomEnum(GppSectionId)]) {
        new RuleEngineModelSchema().tap {
            it.function = GPP_SID_IN
            it.args = new RuleEngineFunctionArgs(sids: argsGppSid)
        }
    }

    static RuleEngineModelSchema createDomainInSchema(List<String> argsDomain = [PBSUtils.randomString]) {
        new RuleEngineModelSchema().tap {
            it.function = DOMAIN_IN
            it.args = new RuleEngineFunctionArgs(domains: argsDomain)
        }
    }

    static RuleEngineModelSchema createBundleInSchema(List<String> argsBundle = [PBSUtils.randomString]) {
        new RuleEngineModelSchema().tap {
            it.function = BUNDLE_IN
            it.args = new RuleEngineFunctionArgs(bundles: argsBundle)
        }
    }

    static RuleEngineModelSchema createMediaTypeInSchema(List<MediaType> argsMediaType = [PBSUtils.getRandomEnum(MediaType)]) {
        new RuleEngineModelSchema().tap {
            it.function = MEDIA_TYPE_IN
            it.args = new RuleEngineFunctionArgs(types: argsMediaType)
        }
    }

    static RuleEngineModelSchema createAdUnitInSchema(List<String> argsMediaType = [PBSUtils.randomString]) {
        new RuleEngineModelSchema().tap {
            it.function = AD_UNIT_CODE_IN
            it.args = new RuleEngineFunctionArgs(codes: argsMediaType)
        }
    }

    static RuleEngineModelSchema createDeviceTypeInSchema(List<Number> argsMediaType = [PBSUtils.randomNumber]) {
        new RuleEngineModelSchema().tap {
            it.function = DEVICE_TYPE_IN
            it.args = new RuleEngineFunctionArgs(types: argsMediaType)
        }
    }

}
