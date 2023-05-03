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
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE

class GppSyncUserActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${SYNC_USER.metricValue}.disallowed.coun"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_OPENX_ADAPTER = "adapter.${OPENX.value}.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_APPNEXUS_ADAPTER = "adapter.${APPNEXUS.value}.activity.${SYNC_USER.metricValue}.disallowed.count"

    private final static int INVALID_STATUS_CODE = 451
    private final static String INVALID_STATUS_MESSAGE = "Bidder sync blocked for privacy reasons"

    def "PBS cookie sync call when bidder allowed in activities should include proper responded with bidders URLs and update processed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | true
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        Condition.getBaseCondition(OPENX.value)                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])                                    | false
        new Condition(componentName: [GENERIC.value], componentType: [ANALYTICS])                                     | false
    }

    def "PBS cookie sync call when bidder rejected in activities should exclude bidders URLs with proper message and update disallowed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders rejected"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [INVALID_STATUS_MESSAGE]

        and: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
    }

    def "PBS cookie sync call when default activity setting set to false should exclude bidders URLs with proper message"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS cookie sync call when sycn user activities with proper condition type only should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS cookie sync call when bidder allowed activities have invalid condition type should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for enrich ufpd with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS cookie sync call when bidder allowed activities have empty rules should skip this rule and emit an warning"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set with empty rules setup"
        def activity = new Activity(defaultAction: false, rules: [])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == ["Invalid rules setup passed"]

        and: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when specific bidder requiring in activities should respond only with specific bidder URL and metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        and: "Activities set for cookie sync with generic bidders rejected"
        def activity = Activity.getDefaultActivity(activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [INVALID_STATUS_MESSAGE]

        and: "Response should contain URL for Openx bidder"
        def syncOpenxStatus = response.getBidderUserSync(OPENX)
        assert syncOpenxStatus.userSync.url

        and: "Response should not contain URL for Generic bidders"
        def syncGenericStatus = response.getBidderUserSync(GENERIC)
        assert !syncGenericStatus.userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(OPENX.value), true)]]
    }

    def "PBS cookie sync call when first rule allowing in activities should respond with required bidder URL"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.url"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when first rule disallowing in activities should exclude bidders URLs with proper message"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == [INVALID_STATUS_MESSAGE]

        and: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS setuid request when bidder allowed in activities should respond with valid bidders UIDs cookies and update processed metrics"() {
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert !metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE]
        assert !metrics[DISALLOWED_COUNT_FOR_ACCOUNT]
        assert !metrics[DISALLOWED_COUNT_FOR_OPENX_ADAPTER]

        where:
        conditions                                                                                                  | isAllowed
        Condition.getBaseCondition(OPENX.value)                                                                     | true
        new Condition(componentName: [OPENX.value], componentType: [GENERAL_MODULE])                                | true
        new Condition(componentName: [OPENX.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        Condition.getBaseCondition(APPNEXUS.value)                                                                  | false
        new Condition(componentName: [OPENX.value], componentType: [RTD_MODULE])                                    | false
        new Condition(componentName: [OPENX.value], componentType: [ANALYTICS])                                     | false
    }

    def "PBS setuid request when bidder restriction by activities should reject bidders with status code invalidStatusCode and update disallowed metrics"() {
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_OPENX_ADAPTER] == 1

        where:
        conditions                                                                                                  | isAllowed
        Condition.getBaseCondition(OPENX.value)                                                                     | false
        new Condition(componentName: [OPENX.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [OPENX.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
    }

    def "PBS setuid when default activity setting set to false should reject bidders with status code invalidStatusCode"() {
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
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(OPENX.value))])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid request when sync user activities with proper condition type only should respond with valid bidders UIDs cookies"() {
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS setuid request when sync user allowed activities have invalid condition type should reject bidders with status code invalidStatusCode"() {
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS setuid request call when bidder allowed activities have empty rules should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Openx uids Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        and: "Activities set with empty rules setup"
        def activity = new Activity(defaultAction: false, rules: [])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid request when specific bidder requiring in activities should respond only with valid bidders UIDs cookies and update metrics"() {
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
        def activity = Activity.getDefaultActivity(activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain only OPENX UIDs cookies"
        assert response.uidsCookie.tempUIDs.size() == 1
        assert response.uidsCookie.tempUIDs[OPENX]

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_APPNEXUS_ADAPTER] == 1

        where:
        activityRules << [
                [ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(APPNEXUS.value), false)],
                [ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(OPENX.value), true),
                 ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(APPNEXUS.value), false)]
        ]
    }

    def "PBS setuid request when first rule allowing in activities should respond with required UIDs cookies"() {
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
        def allowActivity = new ActivityRule(condition: Condition.getBaseCondition(OPENX.value), allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.getBaseCondition(OPENX.value), allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]
    }

    def "PBS setuid request when first rule disallowing in activities should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with different priority"
        def disallowActivity = new ActivityRule(condition: Condition.getBaseCondition(OPENX.value), allow: false)
        def allowActivity = new ActivityRule(condition: Condition.getBaseCondition(OPENX.value), allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "UIDs cookies for Openx bidders"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }
}
