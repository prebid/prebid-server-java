package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.AccountStatus
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountCookieSyncConfig
import org.prebid.server.functional.model.config.AccountCoopSyncConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Consent
import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.model.request.setuid.UidWithExpiry
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.EMPTY
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

class GppSyncUserActivitiesSpec extends BaseSpec {
    private static final ActivityType type = SYNC_USER
    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync"
    private static final Map<String, String> GENERIC_CONFIG = ["adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
                                                               "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX.value}.enabled"                    : "true",
                                                             "adapters.${OPENX.value}.usersync.cookie-family-name": OPENX.value]
    private static final Map<String, String> PBS_CONFIG = OPENX_CONFIG + GENERIC_CONFIG
    private PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS cookie sync with all bidder allowed in activities should include proper responded with all bidders URLs"() {
        given: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 2

        and: "Response should contain coop-synced bidder URLs"
        assert response.getBidderUserSync(GENERIC).userSync.url
        assert response.getBidderUserSync(OPENX).userSync.url

        where:
        conditions                                                                                    | isAllowed
        [new Condition(componentName: [GENERIC.value, OPENX.value])]                                  | true
        [new Condition(componentName: [GENERIC.value, OPENX.value], componentType: [BIDDER])]         | true
        [new Condition(componentName: [GENERIC.value, OPENX.value], componentType: [GENERAL_MODULE])] | true
        [new Condition(componentName: [APPNEXUS.value])]                                              | false
        [new Condition(componentType: [BIDDER])]                                                      | true
        [new Condition(componentType: [GENERAL_MODULE])]                                              | true
        [new Condition(componentType: [RTD_MODULE])]                                                  | false
    }

    def "PBS cookie sync with all bidder rejected in activities should exclude all bidders URLs with proper message"() {
        given: "Activities set for cookie sync with all bidders rejected"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == ["Bidder sync blocked for privacy reasons"]

        and: "Response should not contain any URLs for bidders"
        assert response.bidderStatus.userSync.url

        where:
        conditions                                                                | isAllowed
        [new Condition(componentType: [BIDDER])]                                  | false
        [new Condition(componentName: [GENERIC.value, OPENX.value])]              | false
        [new Condition(componentType: [GENERAL_MODULE])]                          | false
        [new Condition(componentType: [RTD_MODULE])]                              | true
        [new Condition(componentName: [APPNEXUS.value], componentType: [BIDDER])] | true
    }

    def "PBS cookie sync with specific bidder requiring in activities should respond only with specific bidder URL"() {
        given: "Activities set for cookie sync with generic bidders rejected"
        def activity = Activity.getDefaultActivity(rules: activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == ["Bidder sync blocked for privacy reasons"]

        and: "Response should contain URL for Openx bidder"
        def syncOpenxStatus = response.getBidderUserSync(OPENX)
        assert syncOpenxStatus.userSync.url

        and: "Response should not contain URL for Generic bidders"
        def syncGenericStatus = response.getBidderUserSync(GENERIC)
        assert !syncGenericStatus.userSync.url

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX))],
                          [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX))]]
    }

    def "PBS cookie sync with invalid activities should be ignored in process"() {
        given: "Activities set for cookie sync with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC, OPENX]
            it.account = accountId
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 2

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC).userSync.url
        assert response.getBidderUserSync(OPENX).userSync.url

        where:
        activity << [Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], true),
                     Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], true),
                     Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], true),
                     Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], false),
                     Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], false),
                     Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS cookie sync with specific allow hierarchy in activities should respond with required bidder URL"() {
        given: "Activities set for cookie sync with all bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
            it.account = accountId
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 1

        and: "Response should contain coop-synced bidder"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        rules << [[new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                   new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                   new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: false)]]
    }

    def "PBS cookie sync with specific reject hierarchy in activities should respond with proper warning"() {
        given: "Activities set for cookie sync with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
            it.account = accountId
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain proper warning"
        assert response.warnings == ["Bidder sync blocked for privacy reasons"]

        and: "Response should not contain any URLs for bidders"
        assert response.bidderStatus.userSync.url
    }

    def "PBS cookie sync with invalid hierarchy in activities should ignore activities and respond with bidder URL"() {
        given: "Activities set for cookie sync with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync request with link to account"
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            bidders = [GENERIC]
            it.account = accountId
        }

        when: "PBS processes cookie sync request"
        def response = prebidServerService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain synced bidder"
        assert response.bidderStatus.size() == 1

        and: "Response should contain coop-synced bidder url"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS setuid request with no bidder restriction in activities should respond with all valid bidders UIDs cookies"() {
        given: "Activities set for cookie sync with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX)   : UidWithExpiry.defaultUidWithExpiry,
                        (APPNEXUS): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        conditions                                                                                    | isAllowed
        [new Condition(componentName: [GENERIC.value, OPENX.value], componentType: [BIDDER])]         | true
        [new Condition(componentName: [GENERIC.value, OPENX.value])]                                  | true
        [new Condition(componentName: [APPNEXUS.value])]                                              | false
        [new Condition(componentName: [GENERIC.value, OPENX.value], componentType: [GENERAL_MODULE])] | true
        [new Condition(componentType: [BIDDER])]                                                      | true
        [new Condition(componentType: [GENERAL_MODULE])]                                              | true
        [new Condition(componentType: [RTD_MODULE])]                                                  | false
    }

    def "PBS setuid with all bidder restriction by activities should reject bidders with status code 451"() {
        given: "Activities set for cookie sync with all bidders rejected"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        and: "UIDs cookies for Openx and Appnexus bidders"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX)   : UidWithExpiry.defaultUidWithExpiry,
                        (APPNEXUS): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"

        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "Bidder sync blocked for privacy reasons"

        where:
        conditions                                                                | isAllowed
        [new Condition(componentType: [BIDDER])]                                  | false
        [new Condition(componentName: [GENERIC.value, OPENX.value])]              | false
        [new Condition(componentType: [GENERAL_MODULE])]                          | false
        [new Condition(componentType: [RTD_MODULE])]                              | true
        [new Condition(componentName: [APPNEXUS.value], componentType: [BIDDER])] | true
    }

    def "PBS setuid request with targeting bidder restriction in activities should respond only with this bidder"() {
        given: "Activities set for cookie sync with only Openx bidder allowed"
        def activity = Activity.getDefaultActivity(rules: activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX)   : UidWithExpiry.defaultUidWithExpiry,
                        (APPNEXUS): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain only OPENX UIDs cookies"
        assert response.uidsCookie.tempUIDs.size() == 1
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(APPNEXUS), false)],
                          [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX))]]
    }

    def "PBS setuid request with invalid activities should be ignored in process"() {
        given: "Activities set for cookie sync with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX)   : UidWithExpiry.defaultUidWithExpiry,
                        (APPNEXUS): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[APPNEXUS]
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        activity << [Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], true),
                     Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], true),
                     Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], true),
                     Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], false),
                     Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], false),
                     Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS setuid request with specific allow hierarchy in activities should respond with required UIDs cookies"() {
        given: "Activities set for cookie sync with Openx bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]

        where:
        rules << [[new ActivityRule(priority: HIGHEST, condition: Condition.getBaseCondition(OPENX), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: Condition.getBaseCondition(OPENX), allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: Condition.getBaseCondition(OPENX), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: Condition.getBaseCondition(OPENX), allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: false)],
                  [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: true),
                   new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: false)]]
    }

    def "PBS setuid request with specific reject hierarchy in activities should reject bidders with status code 451"() {
        given: "Activities set for cookie sync with Openx bidder rejected by hierarchy structure"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.getBaseCondition(OPENX),
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.getBaseCondition(OPENX),
                allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        and: "UIDs cookies for Openx and Appnexus bidders"
        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes setuid request"
        prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"

        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 451
        assert exception.responseBody == "Bidder sync blocked for privacy reasons"
    }

    def "PBS setuid request with invalid hierarchy in activities should ignore activities and respond with UIDs cookies"() {
        given: "Activities set for cookie sync with invalid priority"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.getBaseCondition(OPENX), allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with cookie sync and allow activities setup"
        def accountId = PBSUtils.randomNumber
        def account = getSyncAccount(accountId as String, activities)
        accountDao.save(account)

        and: "Cookie sync SetuidRequest with allow activities set"
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = account
        }

        def uidsCookie = UidsCookie.defaultUidsCookie.tap {
            tempUIDs = [(OPENX): UidWithExpiry.defaultUidWithExpiry]
        }

        when: "PBS processes cookie sync request without cookies"
        def response = prebidServerService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain UIDs cookies"
        assert response.uidsCookie.tempUIDs[OPENX]
    }

    Account getSyncAccount(String accountId, AllowActivities activities) {
        def consent = new Consent(allowActivities: activities)
        def privacy = new AccountPrivacyConfig(consent: consent)
        def cookieSyncConfig = new AccountCookieSyncConfig(coopSync: new AccountCoopSyncConfig(enabled: false))
        def accountConfig = new AccountConfig(status: AccountStatus.INACTIVE, cookieSync: cookieSyncConfig, privacy: privacy)
        new Account(uuid: accountId, config: accountConfig)
    }
}
