package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.response.auction.ActivityInfrastructure
import org.prebid.server.functional.model.response.auction.ActivityInvocationPayload
import org.prebid.server.functional.model.response.auction.And
import org.prebid.server.functional.model.response.auction.GeoCode
import org.prebid.server.functional.model.response.auction.RuleConfiguration
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.UspNatV1Consent

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CO_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.BASIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ARIZONA

class ActivityTraceLogSpec extends PrivacyBaseSpec {

    def "PBS auction shouldn't log info about activity in response when ext.prebid.trace=null"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = null
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.defaultActivity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def bidResponse = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response shouldn't contain trace"
        assert !bidResponse.ext.debug.trace
    }

    def "PBS auction should log info about activity in response when ext.prebid.trace=base and allow=false"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = BASIC
            device = new Device(geo: new Geo(country: USA, region: ALABAMA.abbreviation))
            setAccountId(accountId)
        }

        and: "Activities set with all bidders rejected"
        def allow = false
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, allow)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def bidResponse = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain basic info in debug"
        def infrastructure = bidResponse.ext.debug.trace.activityInfrastructure
        def fetchBidsActivity = getActivityByName(infrastructure, "fetchBids")
        assert fetchBidsActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                          "Setting the default invocation result.",
                                                          "Processing rule.",
                                                          "Activity Infrastructure invocation result."])
        assert fetchBidsActivity.activityInvocationPayload.every { it == null }
        assert fetchBidsActivity.ruleConfiguration.every { it == null }
        assert fetchBidsActivity.allowByDefault.contains(activity.defaultAction)
        assert fetchBidsActivity.result.contains("DISALLOW")
        assert fetchBidsActivity.country.every { it == null }
        assert fetchBidsActivity.region.every { it == null }
    }

    def "PBS auction should log info about activity in response when ext.prebid.trace=base and allow=true"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = BASIC
            device = new Device(geo: new Geo(country: USA, region: ALABAMA.abbreviation))
            setAccountId(accountId)
        }

        and: "Set up activities"
        def allow = true
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, allow)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def bidResponse = pbsServiceFactory.getService(PBS_CONFIG).sendAuctionRequest(bidRequest)

        then: "Bid response should contain basic info in debug"
        def infrastructure = bidResponse.ext.debug.trace.activityInfrastructure
        def fetchBidsActivity = getActivityByName(infrastructure, "fetchBids")
        assert fetchBidsActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                          "Setting the default invocation result.",
                                                          "Processing rule.",
                                                          "Activity Infrastructure invocation result."])
        assert fetchBidsActivity.activityInvocationPayload.every { it == null }
        assert fetchBidsActivity.allowByDefault.contains(activity.defaultAction)
        assert fetchBidsActivity.ruleConfiguration.every { it == null }
        assert fetchBidsActivity.result.contains("ALLOW")
        assert fetchBidsActivity.country.every { it == null }
        assert fetchBidsActivity.region.every { it == null }

        def transmitUfpdActivity = getActivityByName(infrastructure, "transmitUfpd")
        assert transmitUfpdActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                             "Setting the default invocation result.",
                                                             "Activity Infrastructure invocation result."])
        assert transmitUfpdActivity.activityInvocationPayload.every { it == null }
        assert transmitUfpdActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitUfpdActivity.ruleConfiguration.every { it == null }
        assert transmitUfpdActivity.result.every { it == null }
        assert transmitUfpdActivity.country.every { it == null }
        assert transmitUfpdActivity.region.every { it == null }

        def transmitPreciseGeoActivity = getActivityByName(infrastructure, "transmitPreciseGeo")
        assert transmitPreciseGeoActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                                   "Setting the default invocation result.",
                                                                   "Activity Infrastructure invocation result."])
        assert transmitPreciseGeoActivity.activityInvocationPayload.every { it == null }
        assert transmitPreciseGeoActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitPreciseGeoActivity.ruleConfiguration.every { it == null }
        assert transmitPreciseGeoActivity.result.every { it == null }
        assert transmitPreciseGeoActivity.country.every { it == null }
        assert transmitPreciseGeoActivity.region.every { it == null }

        def transmitTidActivity = getActivityByName(infrastructure, "transmitTid")
        assert transmitTidActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                            "Setting the default invocation result.",
                                                            "Activity Infrastructure invocation result."])
        assert transmitTidActivity.activityInvocationPayload.every { it == null }
        assert transmitTidActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitTidActivity.ruleConfiguration.every { it == null }
        assert transmitTidActivity.result.every { it == null }
        assert transmitTidActivity.country.every { it == null }
        assert transmitTidActivity.region.every { it == null }
    }

    def "PBS auction should log info about activity in response when ext.prebid.trace=verbose and allow=#allow"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            device = new Device(geo: new Geo(country: USA, region: ALABAMA.abbreviation))
            regs.ext.gpc = PBSUtils.randomString
            regs.gppSid = [USP_CA_V1.intValue]
            setAccountId(accountId)
        }

        and: "Set up activities"
        def gpc = PBSUtils.randomString
        def condition = Condition.baseCondition.tap {
            it.gppSid = [USP_CO_V1.intValue]
            it.gpc = gpc
            it.geo = [CAN.withState(ARIZONA)]
        }
        def activityRule = ActivityRule.getDefaultActivityRule(condition, allow)
        def activity = Activity.getDefaultActivity([activityRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def bidResponse = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain basic info in debug"
        def infrastructure = bidResponse.ext.debug.trace.activityInfrastructure
        def fetchBidsActivity = getActivityByName(infrastructure, "fetchBids")
        assert fetchBidsActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                          "Setting the default invocation result.",
                                                          "Processing rule.",
                                                          "Activity Infrastructure invocation result."])
        assert fetchBidsActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert fetchBidsActivity.ruleConfiguration.contains(new RuleConfiguration(
                componentNames: condition.componentName,
                componentTypes: condition.componentType,
                allow: allow,
                gppSidsMatched: false,
                gpc: gpc,
                geoCodes: [new GeoCode(country: CAN, region: ARIZONA.abbreviation)]))
        assert fetchBidsActivity.allowByDefault.contains(activity.defaultAction)
        assert fetchBidsActivity.result.contains("ABSTAIN")
        assert fetchBidsActivity.country.every { it == null }
        assert fetchBidsActivity.region.every { it == null }

        def transmitUfpdActivity = getActivityByName(infrastructure, "transmitUfpd")
        assert transmitUfpdActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                             "Setting the default invocation result.",
                                                             "Activity Infrastructure invocation result."])
        assert transmitUfpdActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitUfpdActivity.ruleConfiguration.every { it == null }
        assert transmitUfpdActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitUfpdActivity.result.every { it == null }
        assert transmitUfpdActivity.country.every { it == null }
        assert transmitUfpdActivity.region.every { it == null }

        def transmitPreciseGeoActivity = getActivityByName(infrastructure, "transmitPreciseGeo")
        assert transmitPreciseGeoActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                                   "Setting the default invocation result.",
                                                                   "Activity Infrastructure invocation result."])
        assert transmitPreciseGeoActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitPreciseGeoActivity.ruleConfiguration.every { it == null }
        assert transmitPreciseGeoActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitPreciseGeoActivity.result.every { it == null }
        assert transmitPreciseGeoActivity.country.every { it == null }
        assert transmitPreciseGeoActivity.region.every { it == null }

        def transmitTidActivity = getActivityByName(infrastructure, "transmitTid")
        assert transmitTidActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                            "Setting the default invocation result.",
                                                            "Activity Infrastructure invocation result."])
        assert transmitTidActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitTidActivity.ruleConfiguration.every { it == null }
        assert transmitTidActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitTidActivity.result.every { it == null }
        assert transmitTidActivity.country.every { it == null }
        assert transmitTidActivity.region.every { it == null }

        where:
        allow << [false, true]
    }

    def "PBS auction should log info about activity in response when ext.prebid.trace=verbose and allow=#allow and privacy module"() {
        given: "Default bid request"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            device = new Device(geo: new Geo(country: USA, region: ALABAMA.abbreviation))
            regs.ext.gpc = PBSUtils.randomString
            regs.gppSid = [USP_CA_V1.intValue]
            regs.gpp = new UspNatV1Consent.Builder().setGpc(true).build()
            setAccountId(accountId)
        }

        and: "Set up activities"
        def gpc = PBSUtils.randomString
        def condition = Condition.baseCondition.tap {
            it.gpc = gpc
            it.geo = [CAN.withState(ARIZONA)]
        }
        def activityRule = ActivityRule.getDefaultActivityRule(condition, allow).tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }
        def activity = Activity.getDefaultActivity([activityRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Save account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        def bidResponse = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain verbose info in debug"
        def infrastructure = bidResponse.ext.debug.trace.activityInfrastructure
        def fetchBidsActivity = getActivityByName(infrastructure, "fetchBids")
        assert fetchBidsActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                          "Setting the default invocation result.",
                                                          "Processing rule.",
                                                          "Activity Infrastructure invocation result."])
        assert fetchBidsActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert fetchBidsActivity.ruleConfiguration.contains(new RuleConfiguration(
                and: [new And(and: ["USNatDefault. Precomputed result: ABSTAIN."])]))
        assert fetchBidsActivity.allowByDefault.contains(activity.defaultAction)
        assert fetchBidsActivity.result.contains("ABSTAIN")
        assert fetchBidsActivity.country.every { it == null }
        assert fetchBidsActivity.region.every { it == null }

        def transmitUfpdActivity = getActivityByName(infrastructure, "transmitUfpd")
        assert transmitUfpdActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                             "Setting the default invocation result.",
                                                             "Activity Infrastructure invocation result."])
        assert transmitUfpdActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitUfpdActivity.ruleConfiguration.every { it == null }
        assert transmitUfpdActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitUfpdActivity.result.every { it == null }
        assert transmitUfpdActivity.country.every { it == null }
        assert transmitUfpdActivity.region.every { it == null }

        def transmitPreciseGeoActivity = getActivityByName(infrastructure, "transmitPreciseGeo")
        assert transmitPreciseGeoActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                                   "Setting the default invocation result.",
                                                                   "Activity Infrastructure invocation result."])
        assert transmitPreciseGeoActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitPreciseGeoActivity.ruleConfiguration.every { it == null }
        assert transmitPreciseGeoActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitPreciseGeoActivity.result.every { it == null }
        assert transmitPreciseGeoActivity.country.every { it == null }
        assert transmitPreciseGeoActivity.region.every { it == null }

        def transmitTidActivity = getActivityByName(infrastructure, "transmitTid")
        assert transmitTidActivity.description.containsAll(["Invocation of Activity Infrastructure.",
                                                            "Setting the default invocation result.",
                                                            "Activity Infrastructure invocation result."])
        assert transmitTidActivity.activityInvocationPayload.contains(new ActivityInvocationPayload(
                componentName: GENERIC.value,
                componentType: BIDDER,
                gpc: bidRequest.regs.ext.gpc,
                region: "AL",
                country: "USA"))
        assert transmitTidActivity.ruleConfiguration.every { it == null }
        assert transmitTidActivity.allowByDefault.contains(activity.defaultAction)
        assert transmitTidActivity.result.every { it == null }
        assert transmitTidActivity.country.every { it == null }
        assert transmitTidActivity.region.every { it == null }

        where:
        allow << [false, true]
    }

    private List<ActivityInfrastructure> getActivityByName(List<ActivityInfrastructure> activityInfrastructures,
                                                           String activity) {
        def firstIndex = activityInfrastructures.findLastIndexOf { it -> it.activity == activity }
        def lastIndex = activityInfrastructures.findIndexOf { it -> it.activity == activity }
        activityInfrastructures[new IntRange(true, firstIndex, lastIndex)]
    }
}

