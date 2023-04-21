package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
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

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class GppEnrichUfpdActivitiesSpec extends PrivacyBaseSpec {

    private String activityProcessedRulesForAccount
    private String disallowedCountForAccount
    private String activityRulesProcessedCount = 'requests.activity.processedrules.count'
    private String disallowedCountForActivityRule = "requests.activity.${ENRICH_UFPD.value}.disallowed.count"
    private String disallowedCountForGenericAdapter = "adapter.${GENERIC.value}.activity.${ENRICH_UFPD.value}.disallowed.count"
    private String disallowedCountForOpenxAdapter = "adapter.${OPENX.value}.activity.${ENRICH_UFPD.value}.disallowed.count"

    private static final GeneralPlanner generalPlanner = new GeneralPlanner(Dependencies.networkServiceContainer)
    private static final UserData userData = new UserData(Dependencies.networkServiceContainer)
    private static final Device testDevice = new Device().tap {
        it.os = PBSUtils.randomizeCase("iOS")
        it.osv = "14.0"
        it.ext = new DeviceExt(atts: randomAttribute)
    }

    def "PBS activities call when enrich UFDP activities is allowing should enhance user.data and provide proper metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set with bidder allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${ENRICH_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount] + 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule]
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount]
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | true
        Condition.getBaseCondition(OPENX.value)                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])     | false
    }

    def "PBS activities call when bidder allowed activities have empty condition type should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for enrich ufpd with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data
    }

    def "PBS activities call when enrich UFDP activities is rejecting should not enhance user.data and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set with bidder allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${ENRICH_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data == generalBidRequest?.user?.data

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter] + 1

        and: "Metrics processed across activities should not be updated"
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | false
    }

    def "PBS activities call when default activity setting off should not enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for enrich ufpd with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data == generalBidRequest?.user?.data
    }

    def "PBS activities call when activities settings set to empty should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS activities call when higher priority allow hierarchy in activities should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data
    }

    def "PBS activities call when specific allow hierarchy in enrich UFDP activities should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activity rules with same priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data
    }

    def "PBS activities call when specific reject hierarchy in enrich UFDP activities should not enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data == generalBidRequest?.user?.data
    }

    def "PBS activities call when invalid hierarchy in enrich UFDP activities should enhance user.data"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, invalidActivity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "Sending auction request to PBS"
        activityPbsService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data
    }

    def "PBS activities call when enrich UFDP activities is allowing should enhance request.device and provide proper metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${ENRICH_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount] + 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule]
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount]
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | true
        Condition.getBaseCondition(OPENX.value)                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])     | false
    }

    def "PBS activities call when bidder allowed activities have empty condition type should enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${ENRICH_UFPD.value}.disallowed.coun"

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    def "PBS activities call when enrich UFDP activities is restricting should not enhance request.device and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | false
    }

    def "PBS activity call when default activity setting off should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set with all bidders allowed"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt
    }

    def "PBS activities call when specific bidder in enrich UFDP activities should enhance request.device only bidder"() {
        given: "Generic and Openx bid requests with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }
        def openxBidRequest = getBidRequestWithAccount(APP, accountId, OPENX).tap {
            it.device = testDevice
        }

        and: "Reject activities setup"
        def activity = Activity.getDefaultActivity(rules: activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${ENRICH_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)
        def openxResponse = activityPbsService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Generic bidder should not enhance device in request"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt

        then: "Openx bidder should not enhance device in request"
        def openxBidderRequest = bidder.getBidderRequest(openxBidRequest.id)
        assert openxBidderRequest.device.lmt == 1

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert metrics[activityProcessedRulesForAccount] == initialMetrics[activityProcessedRulesForAccount] + 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter] + 1

        and: "Metrics for disallowed activities for Openx should stay the same"
        assert metrics[disallowedCountForOpenxAdapter] == initialMetrics[disallowedCountForOpenxAdapter]

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value), true)]]
    }

    def "PBS activities call when activities settings set to empty should enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Empty activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)


        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS activities call when higher priority allow hierarchy in enrich UFDP activities should enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    def "PBS activities call when confuse in allowing on same priority level in enrich UFDP activities should enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activity rules with higher priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    def "PBS activities call when specific reject hierarchy in enrich UFDP activities should not enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt
    }

    def "PBS activities call when invalid hierarchy in enrich UFDP activities should enhance request.device"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, invalidActivity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Default device with device.os = iOS and any device.ext.atts"

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    private static getRandomAttribute() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

    protected void updateLineItemsAndWait() {
        def initialPlansRequestCount = generalPlanner.recordedPlansRequestCount
        activityPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == initialPlansRequestCount + 1 }
    }
}
