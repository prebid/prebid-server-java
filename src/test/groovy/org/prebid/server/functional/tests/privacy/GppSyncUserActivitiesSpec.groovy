package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.setuid.UidWithExpiry
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE

class GppSyncUserActivitiesSpec extends PrivacyBaseSpec {

    private String activityProcessedRulesForAccount
    private String disallowedCountForAccount
    private String activityRulesProcessedCount = 'requests.activity.processedrules.count'
    private String disallowedCountForActivityRule = "requests.activity.${SYNC_USER.value}.disallowed.count"
    private String disallowedCountForGenericAdapter = "adapter.${GENERIC.value}.activity.${SYNC_USER.value}.disallowed.count"
    private String disallowedCountForOpenxAdapter = "adapter.${OPENX.value}.activity.${SYNC_USER.value}.disallowed.count"
    private String disallowedCountForAppnexusAdapter = "adapter.${APPNEXUS.value}.activity.${SYNC_USER.value}.disallowed.count"

    private final static int invalidStatusCode = 451
    private final static String invalidStatusMessage = "Bidder sync blocked for privacy reasons"

    def "PBS cookie sync with bidder allowed in activities should include proper responded with bidders URLs and metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus

        and: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

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

    def "PBS cookie sync with bidder allowed activities have empty condition type should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus

        and: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync with bidder rejected in activities should exclude bidders URLs with proper message and update disallowed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders rejected"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [invalidStatusMessage]

        and: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

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

    def "PBS cookie sync with default activity setting off should exclude bidders URLs with proper message"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [invalidStatusMessage]

        and: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync with specific bidder requiring in activities should respond only with specific bidder URL and metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        and: "Activities set for cookie sync with generic bidders rejected"
        def activity = Activity.getDefaultActivity(rules: activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [invalidStatusMessage]

        and: "Response should contain URL for Openx bidder"
        def syncOpenxStatus = response.getBidderUserSync(OPENX)
        assert syncOpenxStatus.userSync.url

        and: "Response should not contain URL for Generic bidders"
        def syncGenericStatus = response.getBidderUserSync(GENERIC)
        assert !syncGenericStatus.userSync.url

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

    def "PBS cookie sync with invalid activities should be ignored in process"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 1

        and: "Response should contain bidders userSync.url"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS cookie sync with higher priority allow hierarchy in activities should respond with required bidder URL"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities rules for bidder allowed by hierarchy structure"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 1

        and: "Response should contain bidders userSync.url"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync with confuse in allowing on same priority level should respond with required bidder URL"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 1

        and: "Response should contain bidders userSync.url"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync with specific reject hierarchy in activities should respond with proper warning"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [invalidStatusMessage]

        and: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync with invalid hierarchy in activities should ignore activities and respond with proper warning"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)

        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, invalidActivity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [invalidStatusMessage]

        and: "Response should not contain bidders userSync.url"
        assert !response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS setuid request with bidder allowed in activities should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount] + 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule]
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount]
        assert metrics[disallowedCountForOpenxAdapter] == initialMetrics[disallowedCountForOpenxAdapter]

        where:
        conditions                                                                   | isAllowed
        Condition.getBaseCondition(OPENX.value)                                      | true
        Condition.getBaseCondition(APPNEXUS.value)                                   | false
        new Condition(componentName: [OPENX.value], componentType: [GENERAL_MODULE]) | true
        new Condition(componentName: [OPENX.value], componentType: [RTD_MODULE])     | false
    }

    def "PBS setuid request with bidder allowed activities have empty condition type should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]
    }

    def "PBS setuid with bidder restriction by activities should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with bidder rejection"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == invalidStatusCode
        assert exception.responseBody == invalidStatusMessage

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForOpenxAdapter] == initialMetrics[disallowedCountForOpenxAdapter] + 1

        and: "Metrics processed across activities should not be updated"
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        conditions                                                               | isAllowed
        Condition.getBaseCondition(OPENX.value)                                  | false
        new Condition(componentName: [OPENX.value], componentType: [RTD_MODULE]) | true
    }

    def "PBS setuid with default activity setting off should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with default action set to false"
        def rule = ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value), true)
        def activity = new Activity(defaultAction: false, rules: [rule])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == invalidStatusCode
        assert exception.responseBody == invalidStatusMessage
    }

    def "PBS setuid request with targeting bidder restriction in activities should respond only with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX)   : UidWithExpiry.defaultUidWithExpiry,
                        (APPNEXUS): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with only Openx bidder allowed"
        def activity = Activity.getDefaultActivity(rules: activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${SYNC_USER.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain only OPENX UIDs cookies"
        assert response.uidsCookie.tempUIDs.size() == 1
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert metrics[activityProcessedRulesForAccount] == initialMetrics[activityProcessedRulesForAccount] + 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForOpenxAdapter] == initialMetrics[disallowedCountForOpenxAdapter] + 1

        and: "Metrics for disallowed activities for Appnexus should stay the same"
        assert metrics[disallowedCountForAppnexusAdapter] == initialMetrics[disallowedCountForAppnexusAdapter]

        where:
        activityRules << [
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(APPNEXUS.value), false)],
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value), true),
                 ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(APPNEXUS.value), false)]
        ]
    }

    def "PBS setuid request with invalid activities should be ignored in process"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS setuid request with higher priority allow hierarchy in activities should respond with required UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]
    }

    def "PBS setuid request with higher priority reject hierarchy in activities should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with Openx bidder rejected by hierarchy structure"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        and: "UIDs cookies for Openx bidders"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == invalidStatusCode
        assert exception.responseBody == invalidStatusMessage
    }

    def "PBS setuid request with confuse in allowing on same priority level should respond with required UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activity rules with confuse in allowing on same priority level"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.getBaseCondition(OPENX.value),
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == invalidStatusCode
        assert exception.responseBody == invalidStatusMessage
    }

    def "PBS setuid request with invalid hierarchy in activities should ignore activities and respond with UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set for cookie sync with invalid priority"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.getBaseCondition(OPENX.value), allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, invalidActivity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId as String, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == invalidStatusCode
        assert exception.responseBody == invalidStatusMessage
    }
}
