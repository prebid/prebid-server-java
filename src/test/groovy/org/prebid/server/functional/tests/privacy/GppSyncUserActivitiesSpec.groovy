package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.PrivacyModule
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.util.privacy.model.State.MANITOBA
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ALASKA
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERIC

class GppSyncUserActivitiesSpec extends PrivacyBaseSpec {

    private static final String ALERT_GENERAL = "alert.general"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${SYNC_USER.metricValue}.disallowed.count"

    private final static int INVALID_STATUS_CODE = 451
    private final static String INVALID_STATUS_MESSAGE = "Unavailable For Legal Reasons."

    private static final Map<String, String> GEO_LOCATION = ["geolocation.enabled"                           : "true",
                                                             "geolocation.type"                              : "configuration",
                                                             "geolocation.configurations.[0].address-pattern": "209."]

    def "PBS cookie sync call when bidder allowed in activities should include proper responded with bidders URLs and update processed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS cookie sync call when bidder rejected in activities should exclude bidders URLs with proper message and update disallowed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders rejected"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS cookie sync call when default activity setting set to false should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for cookie sync with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync call when bidder allowed activities have invalid condition type should reject bidders with status code invalidStatusCode"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

        and: "Activities set for enrich ufpd with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.url"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when first rule disallowing in activities should exclude bidders URLs"() {
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync should allow rule when gppSid not intersect"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
            it.gppSid = gppSid
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = conditionGppSid
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1

        where:
        gppSid       | conditionGppSid
        null         | [USP_V1.intValue]
        USP_V1.value | null
    }

    def "PBS cookie sync should disallowed rule when gppSid intersect"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            componentType = null
            componentName = null
            gppSid = [USP_V1.intValue]
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS cookie sync should process rule when geo doesn't intersection"() {
        given: "Pbs config with geo location"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.geo-info.[0].country": countyConfig,
                 "geolocation.configurations.geo-info.[0].region" : regionConfig])

        and: "Cookie sync request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = [USP_V1.intValue]
            it.geo = conditionGeo
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request with header"
        def response = prebidServerService
                .sendCookieSyncRequest(cookieSyncRequest, ["X-Forwarded-For": "209.232.44.21"])

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1

        where:
        countyConfig | regionConfig          | conditionGeo
        null         | null                  | ["$USA.value".toString()]
        USA.value    | ALABAMA.abbreviation  | null
        CAN.value    | ALASKA.abbreviation   | [USA.withState(ALABAMA)]
        null         | MANITOBA.abbreviation | [USA.withState(ALABAMA)]
        CAN.value    | null                  | [USA.withState(ALABAMA)]
    }

    def "PBS cookie sync should disallowed rule when device.geo intersection"() {
        given: "Pbs config with geo location"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.geo-info.[0].country": countyConfig,
                 "geolocation.configurations.geo-info.[0].region" : regionConfig])

        and: "Cookie sync request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
            it.gppSid = null
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = null
            it.geo = conditionGeo
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request with header"
        def response = prebidServerService
                .sendCookieSyncRequest(cookieSyncRequest, ["X-Forwarded-For": "209.232.44.21"])

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        countyConfig | regionConfig         | conditionGeo
        USA.value    | null                 | [USA.value]
        USA.value    | null                 | [USA.withState(ALABAMA)]
        USA.value    | ALABAMA.abbreviation | [USA.withState(ALABAMA)]
    }

    def "PBS cookie sync call when privacy regulation match and disabled should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, PrivacyModule.IAB_ALL, PrivacyModule.ALL]
    }

    def "PBS cookie sync call when privacy regulation restring but sid excluded should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1], false)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when privacy regulation not exist for account should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        and: "Account gpp configuration"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Existed account with cookie sync and empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when privacy regulation have duplicate should include first, exclude bidders URLs and populate metric"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Response should contain proper warning"
        assert response.warnings == ["Invalid allowActivities config for account: ${accountId}"] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS cookie sync call when privacy regulation match and rejecting should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync call when privacy regulation match and rejecting by element in hierarchy should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with multiple privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [PrivacyModule.IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url
    }

    def "PBS cookie sync call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with multiple array").size() == 1
    }

    def "PBS setuid request when bidder allowed in activities should respond with valid bidders UIDs cookies and update processed metrics"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with all bidders allowed"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.responseBody

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS setuid request when bidder restriction by activities should reject bidders with status code invalidStatusCode and update disallowed metrics"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with bidder rejection"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
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
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS setuid when default activity setting set to false should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid request when sync user allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with bidder rejection"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.responseBody

        and: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " + "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS setuid request when first rule allowing in activities should respond with required UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activity rules with different priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes request without cookies"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.responseBody
    }

    def "PBS setuid request when first rule disallowing in activities should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
        }

        and: "UIDs cookies"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activity rules with different priority"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid should allow rule when gppSid not intersect"() {
        given: "Default set uid request"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = conditionGppSid
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1

        where:
        gppSid       | conditionGppSid
        null         | [USP_V1.intValue]
        USP_V1.value | null
    }

    def "PBS setuid shouldn't allow rule when gppSid intersect"() {
        given: "Default set uid request"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = [USP_V1.intValue]
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid should process rule when geo doesn't intersection"() {
        given: "Pbs config with geo location"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig])

        and: "Default set uid request"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
            it.gdpr = null
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.geo = conditionGeo
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes set uid request with header"
        def response = prebidServerService
                .sendSetUidRequest(setuidRequest, uidsCookie, ["X-Forwarded-For": "209.232.44.21"])

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        and: "Metrics processed across activities should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1

        where:
        countyConfig | regionConfig          | conditionGeo
        null         | null                  | [USA.value]
        CAN.value    | ALASKA.abbreviation   | [USA.withState(ALABAMA)]
        null         | MANITOBA.abbreviation | [USA.withState(ALABAMA)]
        CAN.value    | null                  | [USA.withState(ALABAMA)]
    }

    def "PBS setuid should disallowed rule when device.geo intersection"() {
        given: "Pbs config with geo location"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig])

        and: "Default set uid request"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_V1.value
            it.gdpr = null
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = [USP_V1.intValue]
            it.geo = conditionGeo
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes set uid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie, ["X-Forwarded-For": "209.232.44.21"])

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        countyConfig | regionConfig         | conditionGeo
        USA.value    | null                 | [USA.value]
        USA.value    | ALABAMA.abbreviation | [USA.withState(ALABAMA)]
    }

    def "PBS setuid request when privacy regulation match and disabled should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, PrivacyModule.IAB_ALL, PrivacyModule.ALL]
    }

    def "PBS setuid request when privacy regulation restring but sid excluded should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, GppSectionId.values() as List<GppSectionId>, false)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody
    }

    def "PBS setuid request when privacy regulation not exist for account should respond with valid bidders UIDs cookies"() {
        given: "SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with non-existed privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Existed account with cookie sync and empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody
    }

    def "PBS setuid request when privacy regulation have duplicate should include first, respond with valid bidders UIDs cookies and populate metric"() {
        given: "SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS setuid request when privacy regulation match and rejecting should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid request when privacy regulation match and allowing by first element in hierarchy should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with rejecting privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [PrivacyModule.IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE
    }

    def "PBS setuid request when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with multiple array").size() == 1
    }
}
