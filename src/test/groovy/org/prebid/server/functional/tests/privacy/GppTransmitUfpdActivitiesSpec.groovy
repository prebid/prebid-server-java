package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String ALERT_GENERAL = "alert.general"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"

    def "PBS auction call when transmit UFPD activities is allowing requests should leave UFPD fields in request and update proper metrics"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set with generic bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when transmit UFPD activities is rejecting requests should remove UFPD fields in request and update disallowed metrics"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should remove UFPD fields from request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS auction call when bidder allowed activities have empty condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow мяactivities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS auction call when first rule allowing in activities should leave UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS auction call when first rule disallowing in activities should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS auction shouldn't allow rule when gppSid not intersect"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = regsGppSid
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = conditionGppSid
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        regsGppSid        | conditionGppSid
        null              | [USP_V1.intValue]
        [USP_V1.intValue] | null
    }

    def "PBS auction should allow rule when gppSid intersect"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_V1.intValue]
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = [USP_V1.intValue]
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)


        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction should process rule when device.geo doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.regs.gppSid = [USP_V1.intValue]
            it.ext.prebid.trace = VERBOSE
            it.device = new Device(geo: deviceGeo)
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = [USP_V1.intValue]
            it.geo = conditionGeo
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.device.didsha1 == bidRequest.device.didsha1
            bidderRequest.device.didmd5 == bidRequest.device.didmd5
            bidderRequest.device.dpidsha1 == bidRequest.device.dpidsha1
            bidderRequest.device.ifa == bidRequest.device.ifa
            bidderRequest.device.macsha1 == bidRequest.device.macsha1
            bidderRequest.device.macmd5 == bidRequest.device.macmd5
            bidderRequest.device.dpidmd5 == bidRequest.device.dpidmd5
            bidderRequest.user.id == bidRequest.user.id
            bidderRequest.user.buyeruid == bidRequest.user.buyeruid
            bidderRequest.user.yob == bidRequest.user.yob
            bidderRequest.user.gender == bidRequest.user.gender
            bidderRequest.user.eids[0].source == bidRequest.user.eids[0].source
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        deviceGeo                                           | conditionGeo
        null                                                | [USA.value]
        new Geo(country: USA)                               | null
        new Geo(region: ALABAMA.abbreviation)               | [USA.withState(ALABAMA)]
        new Geo(country: CAN, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
    }

    def "PBS auction should disallowed rule when device.geo intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.device = new Device(geo: deviceGeo)
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = null
            it.geo = conditionGeo
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.device.didsha1
            !bidderRequest.device.didmd5
            !bidderRequest.device.dpidsha1
            !bidderRequest.device.ifa
            !bidderRequest.device.macsha1
            !bidderRequest.device.macmd5
            !bidderRequest.device.dpidmd5
            !bidderRequest.user.id
            !bidderRequest.user.buyeruid
            !bidderRequest.user.yob
            !bidderRequest.user.gender
            !bidderRequest.user.eids
            !bidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.value]
        new Geo(country: USA)                               | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
    }

    def "PBS auction should process rule when regs.ext.gpc doesn't intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.device.didsha1 == bidRequest.device.didsha1
            bidderRequest.device.didmd5 == bidRequest.device.didmd5
            bidderRequest.device.dpidsha1 == bidRequest.device.dpidsha1
            bidderRequest.device.ifa == bidRequest.device.ifa
            bidderRequest.device.macsha1 == bidRequest.device.macsha1
            bidderRequest.device.macmd5 == bidRequest.device.macmd5
            bidderRequest.device.dpidmd5 == bidRequest.device.dpidmd5
            bidderRequest.user.id == bidRequest.user.id
            bidderRequest.user.buyeruid == bidRequest.user.buyeruid
            bidderRequest.user.yob == bidRequest.user.yob
            bidderRequest.user.gender == bidRequest.user.gender
            bidderRequest.user.eids[0].source == bidRequest.user.eids[0].source
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should disallowed rule when regs.ext.gpc intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def gpc = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = gpc
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = gpc
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.device.didsha1
            !bidderRequest.device.didmd5
            !bidderRequest.device.dpidsha1
            !bidderRequest.device.ifa
            !bidderRequest.device.macsha1
            !bidderRequest.device.macmd5
            !bidderRequest.device.dpidmd5
            !bidderRequest.user.id
            !bidderRequest.user.buyeruid
            !bidderRequest.user.yob
            !bidderRequest.user.gender
            !bidderRequest.user.eids
            !bidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction should process rule when header gpc doesn't intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = null
            it.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": "1"])

        then: "Generic bidder request should have data in UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.device.didsha1 == bidRequest.device.didsha1
            bidderRequest.device.didmd5 == bidRequest.device.didmd5
            bidderRequest.device.dpidsha1 == bidRequest.device.dpidsha1
            bidderRequest.device.ifa == bidRequest.device.ifa
            bidderRequest.device.macsha1 == bidRequest.device.macsha1
            bidderRequest.device.macmd5 == bidRequest.device.macmd5
            bidderRequest.device.dpidmd5 == bidRequest.device.dpidmd5
            bidderRequest.user.id == bidRequest.user.id
            bidderRequest.user.buyeruid == bidRequest.user.buyeruid
            bidderRequest.user.yob == bidRequest.user.yob
            bidderRequest.user.gender == bidRequest.user.gender
            bidderRequest.user.eids[0].source == bidRequest.user.eids[0].source
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should disallowed rule when header gpc intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = null
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = VALID_VALUE_FOR_GPC_HEADER
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests with header"
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Generic bidder request should have empty UFPD fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.device.didsha1
            !bidderRequest.device.didmd5
            !bidderRequest.device.dpidsha1
            !bidderRequest.device.ifa
            !bidderRequest.device.macsha1
            !bidderRequest.device.macmd5
            !bidderRequest.device.dpidmd5
            !bidderRequest.user.id
            !bidderRequest.user.buyeruid
            !bidderRequest.user.yob
            !bidderRequest.user.gender
            !bidderRequest.user.eids
            !bidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when privacy regulation match and disabled should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        and: "Generic bidder should be called due to positive allow in activities"
        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS auction call when privacy regulation restring but sid excluded should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        and: "Generic bidder should be called due to positive allow in activities"
        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS auction call when privacy regulation not exist for account and allowing should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with non-existed privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Existed account with empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        and: "Generic bidder should be called due to positive allow in activities"
        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS auction call when privacy regulation have duplicate should include first, leave UFPD fields and populate metric"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } == ["Invalid allowActivities config for account: " + accountId] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device.didsha1 == genericBidRequest.device.didsha1
            genericBidderRequest.device.didmd5 == genericBidRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == genericBidRequest.device.dpidsha1
            genericBidderRequest.device.ifa == genericBidRequest.device.ifa
            genericBidderRequest.device.macsha1 == genericBidRequest.device.macsha1
            genericBidderRequest.device.macmd5 == genericBidRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == genericBidRequest.device.dpidmd5
            genericBidderRequest.user.id == genericBidRequest.user.id
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS auction call when privacy regulation match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS auction call when privacy regulation match and rejecting by element in hierarchy should remove UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        and: "Generic bidder should be called due to positive allow in activities"
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS auction call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmit ufpd with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with multiple array").size() == 1
    }

    def "PBS amp call when transmit UFPD activities is allowing request should leave UFPD fields field in active request and update proper metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when transmit UFPD activities is rejecting request should remove UFPD fields field in active request and update disallowed metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should remove UFPD fields from request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when bidder allowed activities have empty condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with have empty condition type"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS amp call when first rule allowing in activities should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS amp call when first rule disallowing in activities should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when privacy regulation match and disabled should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS amp call when privacy regulation restring but sid excluded should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1])

        and: "Existed account with and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS amp call when privacy regulation not exist for account should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS amp call when privacy regulation have duplicate should include first, leave UFPD fields and populate metric"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } ==
                ["Invalid allowActivities config for account: ${accountId}"] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS amp call when privacy regulation match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when privacy regulation match and rejecting by element in hierarchy should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitUfpd with multiple privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit ufpd with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with multiple array").size() == 1
    }

    def "PBS amp should disallowed rule when header.gpc intersection with condition.gpc"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.ext.gpc = null
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = VALID_VALUE_FOR_GPC_HEADER
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device.didsha1
            !genericBidderRequest.device.didmd5
            !genericBidderRequest.device.dpidsha1
            !genericBidderRequest.device.ifa
            !genericBidderRequest.device.macsha1
            !genericBidderRequest.device.macmd5
            !genericBidderRequest.device.dpidmd5
            !genericBidderRequest.user.id
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp should allowed rule when gpc header doesn't intersection with condition.gpc"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            genericBidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            genericBidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            genericBidderRequest.device.ifa == ampStoredRequest.device.ifa
            genericBidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            genericBidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            genericBidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            genericBidderRequest.user.id == ampStoredRequest.user.id
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    private BidRequest givenBidRequestWithAccountAndUfpdData(String accountId) {
        BidRequest.getDefaultBidRequest().tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.device = new Device().tap {
                didsha1 = PBSUtils.randomString
                didmd5 = PBSUtils.randomString
                dpidsha1 = PBSUtils.randomString
                ifa = PBSUtils.randomString
                macsha1 = PBSUtils.randomString
                macmd5 = PBSUtils.randomString
                dpidmd5 = PBSUtils.randomString
            }
            it.user = User.defaultUser
            it.user.eids = [Eid.defaultEid]
            it.user.data = [new Data(name: PBSUtils.randomString)]
            it.user.buyeruid = PBSUtils.randomString
            it.user.yob = PBSUtils.randomNumber
            it.user.gender = PBSUtils.randomString
            it.user.ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
        }
    }
}
