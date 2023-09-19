package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.ActivityConfig
import org.prebid.server.functional.model.config.EqualityValueRule
import org.prebid.server.functional.model.config.InequalityValueRule
import org.prebid.server.functional.model.config.LogicalRestrictedRule
import org.prebid.server.functional.model.config.ModuleConfig
import org.prebid.server.functional.model.config.SidsConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.UspCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspVaV1Consent
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.DataActivity.CONSENT
import static org.prebid.server.functional.model.config.DataActivity.NOTICE_NOT_PROVIDED
import static org.prebid.server.functional.model.config.DataActivity.NOTICE_PROVIDED
import static org.prebid.server.functional.model.config.DataActivity.NOT_APPLICABLE
import static org.prebid.server.functional.model.config.DataActivity.NO_CONSENT
import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.AND
import static org.prebid.server.functional.model.config.LogicalRestrictedRule.LogicalOperation.OR
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.CHILD_CONSENTS_BELOW_13
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.CHILD_CONSENTS_FROM_13_TO_16
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.GPC
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_ACCOUNT_INFO
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_BIOMETRIC_ID
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_CITIZENSHIP_STATUS
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_COMMUNICATION_CONTENTS
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_GENETIC_ID
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_GEOLOCATION
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_HEALTH_INFO
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_ID_NUMBERS
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_ORIENTATION
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SENSITIVE_DATA_RELIGIOUS_BELIEFS
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.SHARING_NOTICE
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_VA_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.util.privacy.model.State.MANITOBA
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ALASKA
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL

class GppSyncUserActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${SYNC_USER.metricValue}.disallowed.count"
    private static final String ALERT_GENERAL = "alerts.general"

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

        and: "Activities set for invalid input"
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

    def "PBS cookie sync call when privacy regulation match and rejecting should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS cookie sync call when privacy module contain some part of disallow logic should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
            it.gpp = disallowGppLogic
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build(),
                new UspNatV1Consent.Builder().setSaleOptOut(1).build(),
                new UspNatV1Consent.Builder().setSaleOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSaleOptOutNotice(0).setSaleOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingNotice(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOutNotice(0).setSharingOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingNotice(0).setSharingOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOut(1).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOut(1).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOutNotice(0).setTargetedAdvertisingOptOut(2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UspNatV1Consent.Builder().setPersonalDataConsents(2).build()
        ]
    }

    def "PBS cookie sync call when request have different gpp consent but match and rejecting should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = gppSid.value
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        gppConsent                                                          | gppSid
        new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build() | USP_NAT_V1
        new UspCaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CA_V1
        new UspVaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_VA_V1
        new UspCoV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CO_V1
        new UspUtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_UT_V1
        new UspCtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CT_V1
    }

    def "PBS cookie sync call when privacy modules contain allowing settings should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        and: "Account gpp configuration"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Existed account with cookie sync and empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: [USP_NAT_V1]), enabled: true)
        ]
    }

    def "PBS cookie sync call when regs.gpp in request is allowing should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
            it.gpp = regsGpp
        }

        and: "Activities set for cookie sync with all bidders allowed"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        and: "Account gpp configuration"
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        regsGpp << ["", new UspNatV1Consent.Builder().build(), new UspNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS cookie sync call when privacy regulation have duplicate should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: []), enabled: true)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS cookie sync call when privacy module contain invalid code should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.value
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
            it.account = accountId
        }

        and: "Activities set for cookie sync with privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleIabAll]))

        and: "Invalid account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        and: "Existed account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url
    }

    def "PBS cookie sync call when privacy regulation don't match custom requirement should include proper responded with bidders URLs"() {
        given: "Default basic generic BidRequest"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSolidRestriction(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSolidRestriction(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSolidRestriction(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                        new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS cookie sync when privacy regulation match custom requirement should exclude bidders URLs"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSolidRestriction(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        gppConsent                                                | valueRules
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(false).build()       | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
    }

    def "PBS cookie sync call when custom privacy regulation empty and normalize is disabled should call to bidder without warning"() {
        given: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.gpp = gppConsent
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], restrictedRule), [USP_CT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain warning"
        assert !response.warnings
    }

    def "PBS cookie sync when custom privacy regulation with normalizing should exclude bidders URLs"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppStateConsent.build()
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([SYNC_USER], LogicalRestrictedRule.generateSolidRestriction(AND, equalityValueRules))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        gppSid    | equalityValueRules                                                      | gppStateConsent
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), 1, PBSUtils.getRandomNumber(0, 2))
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

    def "PBS setuid request when privacy regulation match and rejecting should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS setuid request when privacy module contain some part of disallow logic should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.gpp = disallowGppLogic
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build(),
                new UspNatV1Consent.Builder().setSaleOptOut(1).build(),
                new UspNatV1Consent.Builder().setSaleOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSaleOptOutNotice(0).setSaleOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingNotice(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOutNotice(0).setSharingOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingNotice(0).setSharingOptOut(2).build(),
                new UspNatV1Consent.Builder().setSharingOptOut(1).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOut(1).build(),
                new UspNatV1Consent.Builder().setTargetedAdvertisingOptOutNotice(0).setTargetedAdvertisingOptOut(2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UspNatV1Consent.Builder().setPersonalDataConsents(2).build()
        ]
    }

    def "PBS setuid request when request have different gpp consent but match and rejecting should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.value
            it.gpp = gppConsent
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        gppConsent                                                          | gppSid
        new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build() | USP_NAT_V1
        new UspCaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CA_V1
        new UspVaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_VA_V1
        new UspCoV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CO_V1
        new UspUtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_UT_V1
        new UspCtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CT_V1
    }

    def "PBS setuid request when privacy modules contain allowing settings should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        where:
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: [USP_NAT_V1]), enabled: true),

        ]
    }

    def "PBS setuid request when regs.gpp in request is allowing should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.gpp = regsGpp
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        where:
        regsGpp << ["", new UspNatV1Consent.Builder().build(), new UspNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS setuid request when privacy regulation have duplicate should respond with valid bidders UIDs cookies"() {
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
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new SidsConfig(skipSids: []), enabled: true)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS setuid request call when privacy module contain invalid code should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with rejecting privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody
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

    def "PBS cookie sync should disallowed rule when device.geo intersection"() {
        given: "Pbs config with geo location"
        def prebidServerService = pbsServiceFactory.getService(PBS_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig])

        and: "Cookie sync request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
            it.gppSid = null
            it.gdpr = null
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
        USA.value    | ALABAMA.abbreviation | [USA.withState(ALABAMA)]
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

    def "PBS setuid call when privacy regulation don't match custom requirement should respond with required UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()

        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "UIDs cookies"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.responseBody

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSolidRestriction(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSolidRestriction(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSolidRestriction(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                        new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS setuid call when privacy regulation match custom requirement should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "UIDs cookies"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSolidRestriction(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        gppConsent                                                | valueRules
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(false).build()       | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
    }

    def "PBS setuid call when custom privacy regulation empty and normalize is disabled should call to bidder without error"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], restrictedRule), [USP_CT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "No exception was thrown"
        def thrown = noExceptionThrown()
        assert !thrown

        and: "Response should not contain warning"
        assert response.responseBody
    }

    def "PBS setuid call when custom privacy regulation with normalizing should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = USP_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppStateConsent.build()
        }

        and: "UIDs cookies"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([SYNC_USER], LogicalRestrictedRule.generateSolidRestriction(AND, equalityValueRules))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.config = new SidsConfig().tap { it.skipSids = [] }
            it.enabled = true
            it.moduleConfig = ModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == INVALID_STATUS_CODE
        assert exception.responseBody == INVALID_STATUS_MESSAGE

        where:
        gppSid    | equalityValueRules                                                      | gppStateConsent
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), 1, PBSUtils.getRandomNumber(0, 2))
    }
}
