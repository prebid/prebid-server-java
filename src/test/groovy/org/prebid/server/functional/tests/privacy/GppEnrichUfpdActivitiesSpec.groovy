package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner
import org.prebid.server.functional.testcontainers.scaffolding.pg.UserData
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class GppEnrichUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${ENRICH_UFPD.metricValue}.disallowed.coun"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${ENRICH_UFPD.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${ENRICH_UFPD.metricValue}.disallowed.count"

    private static final GeneralPlanner generalPlanner = new GeneralPlanner(Dependencies.networkServiceContainer)
    private static final UserData userData = new UserData(Dependencies.networkServiceContainer)

    def "PBS auction call when enrich UFDP activities is allowing should enhance user.data and update processed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
//            ext.prebid.trace = VERBOSE
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set with bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, Activity.defaultActivity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest.user.data

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when enrich UFDP activities is rejecting should preserve data for the user.data as is and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
//            ext.prebid.trace = VERBOSE
        }

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest?.user?.data == genericBidRequest?.user?.data

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should preserve data for the user.data as is"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for enrich ufpd with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest?.user?.data == genericBidRequest?.user?.data
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule for user.data and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for enrich ufpd with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

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

    def "PBS auction call when first rule allowing in activities should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest?.user?.data
    }

    def "PBS auction call when first rule disallowing in activities should preserve data for the user.data as is"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(genericBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(genericBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest?.user?.data == genericBidRequest?.user?.data
    }

    def "PBS auction call when enrich UFDP activities is allowing should enhance request.device and provide processed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should be enhance with data for device"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when enrich UFDP activities is restricting should preserve data for the request.device as is and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should preserve data for the device as is"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert !genericBidderRequest.device.lmt

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should preserve data for the user.device as is"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should preserve data for the device as is"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert !genericBidderRequest.device.lmt
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule for request.device and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
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

    def "PBS auction call when first rule allowing in activities should enhance user.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            setAccountId(accountId)
        }

        and: "Activity rules with different priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should be enhance with data for device"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    def "PBS auction call when first rule disallowing in activities should preserve data for the user.device as is"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def genericBidRequest = BidderRequest.getDefaultBidRequest(APP).tap {
            it.device = randomDevice
            setAccountId(accountId)
        }

        and: "Activity rules with different priority"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should preserve data for the device as is"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        assert !genericBidderRequest.device.lmt
    }

    private static getRandomAttribute() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

    private void updateLineItemsAndWait() {
        def initialPlansRequestCount = generalPlanner.recordedPlansRequestCount
        activityPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == initialPlansRequestCount + 1 }
    }

    private Device getRandomDevice() {
        new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAttribute)
        }
    }
}