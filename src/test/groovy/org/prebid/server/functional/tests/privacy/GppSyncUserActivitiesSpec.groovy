package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.AccountSetting
import org.prebid.server.functional.model.config.ActivityConfig
import org.prebid.server.functional.model.config.EqualityValueRule
import org.prebid.server.functional.model.config.GppModuleConfig
import org.prebid.server.functional.model.config.InequalityValueRule
import org.prebid.server.functional.model.config.LogicalRestrictedRule
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.cookiesync.CookieSyncRequest
import org.prebid.server.functional.model.request.setuid.SetuidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.UsCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsVaV1Consent
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData

import java.time.Instant

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
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
import static org.prebid.server.functional.model.config.UsNationalPrivacySection.PERSONAL_DATA_CONSENTS
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
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.ALERT_GENERAL
import static org.prebid.server.functional.model.privacy.Metric.PROCESSED_ACTIVITY_RULES_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_VA_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.PublicCountryIp.USA_IP
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ALASKA
import static org.prebid.server.functional.util.privacy.model.State.MANITOBA

class GppSyncUserActivitiesSpec extends PrivacyBaseSpec {

    private static final String GEO_LOCATION_REQUESTS = "geolocation_requests"
    private static final String GEO_LOCATION_SUCCESSFUL = "geolocation_successful"

    private final static int INVALID_STATUS_CODE = 451
    private final static String INVALID_STATUS_MESSAGE = "Unavailable For Legal Reasons."

    private static final Map<String, String> GEO_LOCATION = ["geolocation.enabled"                           : "true",
                                                             "geolocation.type"                              : "configuration",
                                                             "geolocation.configurations.[0].address-pattern": USA_IP.v4]

    def "PBS cookie sync call when bidder allowed in activities should include proper responded with bidders URLs and update processed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.defaultActivity),
                       new AllowActivities().tap { syncUserKebabCase = Activity.defaultActivity },
                       new AllowActivities().tap { syncUserKebabCase = Activity.defaultActivity },
        ]
    }

    def "PBS cookie sync call when bidder rejected in activities should exclude bidders URLs with proper message and update disallowed metrics"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.account = accountId
        }

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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])),
                       new AllowActivities().tap { syncUserKebabCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
                       new AllowActivities().tap { syncUserKebabCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
        ]
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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1
    }

    def "PBS cookie sync call when privacy regulation match and rejecting should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulations])

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
            it.gppSid = US_NAT_V1.value
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
                new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build(),
                new UsNatV1Consent.Builder().setSaleOptOut(1).setSaleOptOutNotice(1).setMspaServiceProviderMode(2).setMspaOptOutOptionMode(1).build(),
                new UsNatV1Consent.Builder().setSaleOptOutNotice(2).setSaleOptOut(1).setMspaServiceProviderMode(2).setMspaOptOutOptionMode(1).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UsNatV1Consent.Builder().setPersonalDataConsents(2).build(),
                new UsNatV1Consent.Builder()
                        .setSharingNotice(2)
                        .setSharingOptOutNotice(1)
                        .setSharingOptOut(1)
                        .setMspaServiceProviderMode(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSharingOptOutNotice(2)
                        .setSharingOptOut(1)
                        .setSharingNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setTargetedAdvertisingOptOutNotice(2)
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setTargetedAdvertisingOptOut(1)
                        .setTargetedAdvertisingOptOutNotice(1)
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build()
        ]
    }

    def "PBS cookie sync call when privacy module contain some part of disallow logic which violates GPP validation should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
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
                'DBABLA~BAAgAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAACAAAAAAA.QA'
        ]
    }

    def "PBS cookie sync call when privacy module contain invalid GPP string should exclude bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
            it.gpp = invalidGpp
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

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain bidders userSync.urls"
        assert response.getBidderUserSync(GENERIC).userSync.url

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        where:
        invalidGpp << [null, "", PBSUtils.randomString, INVALID_GPP_STRING]
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
        gppConsent                                                                                    | gppSid
        new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build() | US_NAT_V1
        new UsCaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CA_V1
        new UsVaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_VA_V1
        new UsCoV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CO_V1
        new UsUtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_UT_V1
        new UsCtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CT_V1
    }

    def "PBS cookie sync call when privacy modules contain allowing settings should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
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
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: true)
        ]
    }

    def "PBS cookie sync call when regs.gpp in request is allowing should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
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
        regsGpp << ["", new UsNatV1Consent.Builder().build(), new UsNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS cookie sync call when privacy regulation have duplicate should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
        }

        and: "Activities set for cookie sync with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

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
        assert metrics[ALERT_GENERAL.getValue()] == 1
    }

    def "PBS cookie sync call when privacy module contain invalid code should include proper responded with bidders URLs"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
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
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.intValue
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
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
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
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS cookie sync when privacy regulation match custom requirement should exclude bidders URLs"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.intValue
            it.account = accountId
            it.gpp = gppConsent
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        gppConsent                                                      | valueRules
        new UsNatV1Consent.Builder().setPersonalDataConsents(2).build() | [new EqualityValueRule(PERSONAL_DATA_CONSENTS, NOTICE_NOT_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(true).build()               | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(false).build()              | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(true).build()               | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                           new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UsNatV1Consent.Builder().setPersonalDataConsents(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                           new EqualityValueRule(PERSONAL_DATA_CONSENTS, NOTICE_NOT_PROVIDED)]
    }

    def "PBS cookie sync call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.intValue
            it.gpp = gppConsent
            setAccount(accountId)
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
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], restrictedRule), [US_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "Invalid account configuration: JsonLogic exception: " +
                "objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL.getValue()] == 1
    }

    def "PBS cookie sync when custom privacy regulation with normalizing should exclude bidders URLs"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = gppSid.intValue
            it.account = accountId
            it.gpp = gppStateConsent.build()
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([SYNC_USER], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request without cookies"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        gppSid   | equalityValueRules                                                      | gppStateConsent
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)] | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]    | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]   | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]           | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]         | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]     | new UsUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)] | new UsUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UsCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCtV1Consent.Builder()
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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(setuidRequest, SYNC_USER)] == 1
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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(setuidRequest, SYNC_USER)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(setuidRequest, SYNC_USER)] == 1
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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(setuidRequest, SYNC_USER)] == 1

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
            it.gppSid = US_NAT_V1.value
            it.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulations])

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
            it.gppSid = US_NAT_V1.value
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
                new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build(),
                new UsNatV1Consent.Builder().setSaleOptOut(1).setSaleOptOutNotice(1).setMspaServiceProviderMode(2).setMspaOptOutOptionMode(1).build(),
                new UsNatV1Consent.Builder().setSaleOptOutNotice(2).setSaleOptOut(1).setMspaServiceProviderMode(2).setMspaOptOutOptionMode(1).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UsNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UsNatV1Consent.Builder().setPersonalDataConsents(2).build(),
                new UsNatV1Consent.Builder()
                        .setSharingNotice(2)
                        .setSharingOptOutNotice(1)
                        .setSharingOptOut(1)
                        .setMspaServiceProviderMode(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSharingOptOutNotice(2)
                        .setSharingOptOut(1)
                        .setSharingNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setTargetedAdvertisingOptOutNotice(2)
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setTargetedAdvertisingOptOut(1)
                        .setTargetedAdvertisingOptOutNotice(1)
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build()
        ]
    }

    def "PBS setuid request when privacy module contain some part of disallow logic which violates GPP validation should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
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
                'DBABLA~BAAgAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAACAAAAAAA.QA'
        ]
    }

    def "PBS setuid request when privacy module contain invalid GPP string should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.gpp = invalidGpp
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

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain uids cookie"
        assert response.uidsCookie
        assert response.responseBody

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(setuidRequest, SYNC_USER)] == 1

        where:
        invalidGpp << [null, "", PBSUtils.randomString, INVALID_GPP_STRING]
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
        gppConsent                                                                                    | gppSid
        new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build() | US_NAT_V1
        new UsCaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CA_V1
        new UsVaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_VA_V1
        new UsCoV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CO_V1
        new UsUtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_UT_V1
        new UsCtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CT_V1
    }

    def "PBS setuid request when privacy modules contain allowing settings should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
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
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: true),

        ]
    }

    def "PBS setuid request when regs.gpp in request is allowing should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
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
        regsGpp << ["", new UsNatV1Consent.Builder().build(), new UsNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS setuid request when privacy regulation have duplicate should respond with valid bidders UIDs cookies"() {
        given: "SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
        }

        and: "UIDS Cookie"
        def uidsCookie = UidsCookie.defaultUidsCookie

        and: "Activities set for cookie sync with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

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
        assert metrics[ALERT_GENERAL.getValue()] == 1
    }

    def "PBS setuid request call when privacy module contain invalid code should respond with valid bidders UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
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

    def "PBS setuid call when privacy regulation don't match custom requirement should respond with required UIDs cookies"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()

        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = US_NAT_V1.intValue
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
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
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
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS setuid call when privacy regulation match custom requirement should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = US_NAT_V1.intValue
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
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], accountLogic))
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
        gppConsent                                                      | valueRules
        new UsNatV1Consent.Builder().setPersonalDataConsents(2).build() | [new EqualityValueRule(PERSONAL_DATA_CONSENTS, NOTICE_NOT_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(true).build()               | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(false).build()              | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UsNatV1Consent.Builder().setGpc(true).build()               | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                           new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UsNatV1Consent.Builder().setPersonalDataConsents(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                           new EqualityValueRule(PERSONAL_DATA_CONSENTS, NOTICE_NOT_PROVIDED)]
    }

    def "PBS setuid call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomString
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.account = accountId
            it.gpp = gppConsent
            it.gppSid = US_NAT_V1.value
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
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([SYNC_USER], restrictedRule), [US_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes setuid request"
        activityPbsService.sendSetUidRequest(setuidRequest, uidsCookie)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "Invalid account configuration: JsonLogic exception: " +
                "objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL.getValue()] == 1
    }

    def "PBS setuid call when custom privacy regulation with normalizing should reject bidders with status code invalidStatusCode"() {
        given: "Cookie sync SetuidRequest with accountId"
        def accountId = PBSUtils.randomNumber as String
        def setuidRequest = SetuidRequest.defaultSetuidRequest.tap {
            it.gppSid = gppSid.intValue
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
        def activityConfig = new ActivityConfig([SYNC_USER], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
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
        gppSid   | equalityValueRules                                                      | gppStateConsent
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UsCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)] | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]    | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]   | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]           | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]         | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]          | new UsUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]     | new UsUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)] | new UsUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UsCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UsCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UsCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), 1, PBSUtils.getRandomNumber(0, 2))
    }

    def "PBS cookie sync should process rule when geo doesn't intersection"() {
        given: "Pbs config with geo location"
        def pbsConfig = GENERAL_PRIVACY_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.geo-info.[0].country": countyConfig,
                 "geolocation.configurations.geo-info.[0].region" : regionConfig]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        countyConfig  | regionConfig          | conditionGeo
        null          | null                  | ["$USA.ISOAlpha3".toString()]
        USA.ISOAlpha3 | ALABAMA.abbreviation  | null
        CAN.ISOAlpha3 | ALASKA.abbreviation   | [USA.withState(ALABAMA)]
        null          | MANITOBA.abbreviation | [USA.withState(ALABAMA)]
        CAN.ISOAlpha3 | null                  | [USA.withState(ALABAMA)]
    }

    def "PBS setuid should process rule when geo doesn't intersection"() {
        given: "Pbs config with geo location"
        def pbsConfig = GENERAL_PRIVACY_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(setuidRequest, SYNC_USER)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        countyConfig  | regionConfig          | conditionGeo
        null          | null                  | [USA.ISOAlpha3]
        CAN.ISOAlpha3 | ALASKA.abbreviation   | [USA.withState(ALABAMA)]
        null          | MANITOBA.abbreviation | [USA.withState(ALABAMA)]
        CAN.ISOAlpha3 | null                  | [USA.withState(ALABAMA)]
    }

    def "PBS cookie sync should disallowed rule when device.geo intersection"() {
        given: "Pbs config with geo location"
        def pbsConfig = GENERAL_PRIVACY_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        countyConfig  | regionConfig         | conditionGeo
        USA.ISOAlpha3 | null                 | [USA.ISOAlpha3]
        USA.ISOAlpha3 | ALABAMA.abbreviation | [USA.withState(ALABAMA)]
    }

    def "PBS setuid should disallowed rule when device.geo intersection"() {
        given: "Pbs config with geo location"
        def pbsConfig = GENERAL_PRIVACY_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": countyConfig,
                 "geolocation.configurations.[0].geo-info.region" : regionConfig]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        countyConfig  | regionConfig         | conditionGeo
        USA.ISOAlpha3 | null                 | [USA.ISOAlpha3]
        USA.ISOAlpha3 | ALABAMA.abbreviation | [USA.withState(ALABAMA)]
    }

    def "PBS cookie sync should fetch geo once when gpp sync user and account require geo look up"() {
        given: "Pbs config with geo location"
        def pbsConfig = GENERAL_PRIVACY_CONFIG + GEO_LOCATION +
                ["geolocation.configurations.[0].geo-info.country": USA.ISOAlpha3,
                 "geolocation.configurations.[0].geo-info.region" : ALABAMA.abbreviation]
        def prebidServerService = pbsServiceFactory.getService(pbsConfig)

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
            it.geo = [USA.withState(ALABAMA)]
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, activity)

        and: "Flush metrics"
        flushMetrics(prebidServerService)

        and: "Set up account for allow activities"
        def privacy = new AccountPrivacyConfig(ccpa: new AccountCcpaConfig(enabled: true), allowActivities: activities)
        def accountConfig = new AccountConfig(privacy: privacy, settings: new AccountSetting(geoLookup: true))
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes cookie sync request with header"
        def response = prebidServerService
                .sendCookieSyncRequest(cookieSyncRequest, ["X-Forwarded-For": USA_IP.v4])

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        and: "Metrics for disallowed activities should be updated"
        def metrics = prebidServerService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(cookieSyncRequest, SYNC_USER)] == 1

        and: "Metrics processed across activities should be updated"
        assert metrics[GEO_LOCATION_REQUESTS] == 1
        assert metrics[GEO_LOCATION_SUCCESSFUL] == 1

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS cookie sync should exclude bidders URLs when privacy regulation match and personal data consent is 2"() {
        given: "Cookie sync request with link to account"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
            it.gpp = new UsNatV1Consent.Builder().setPersonalDataConsents(2).build()
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulations])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig().tap {
            it.enabled = true
            it.code = IAB_US_GENERAL
        }

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

    def "PBS cookie sync should exclude bidders URLs when privacy regulation match and personal data consent is 2 and allowPersonalDataConsent2 is false"() {
        given: "Cookie sync request with gpp privacy"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
            it.gpp = new UsNatV1Consent.Builder().setPersonalDataConsents(2).build()
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulation])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig().tap {
            enabled = true
            code = IAB_US_GENERAL
            config = gppModuleConfig
        }

        and: "Save account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should not contain any URLs for bidders"
        assert !response.bidderStatus.userSync.url

        where:
        privacyAllowRegulation | gppModuleConfig
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2: false)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2: false)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2: false)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2KebabCase: false)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2KebabCase: false)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2KebabCase: false)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: false)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: false)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: false)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2: null)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2: null)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2: null)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2KebabCase: null)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2KebabCase: null)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2KebabCase: null)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: null)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: null)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: null)
    }

    def "PBS cookie sync shouldn't exclude bidders URLs when privacy regulation match and personal data consent is 2 and allowPersonalDataConsent2 is true"() {
        given: "Cookie sync request with gpp privacy"
        def accountId = PBSUtils.randomString
        def cookieSyncRequest = CookieSyncRequest.defaultCookieSyncRequest.tap {
            it.gppSid = US_NAT_V1.value
            it.account = accountId
            it.gpp = new UsNatV1Consent.Builder().setPersonalDataConsents(2).build()
        }

        and: "Activities set for cookie sync with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulation])
        def activities = AllowActivities.getDefaultAllowActivities(SYNC_USER, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig().tap {
            enabled = true
            code = IAB_US_GENERAL
            config = gppModuleConfig
        }

        and: "Save account with cookie sync and privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes cookie sync request"
        def response = activityPbsService.sendCookieSyncRequest(cookieSyncRequest)

        then: "Response should contain any URLs for bidders"
        assert response.bidderStatus.userSync.url

        where:
        privacyAllowRegulation | gppModuleConfig
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2: true)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2: true)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2: true)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2KebabCase: true)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2KebabCase: true)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2KebabCase: true)
        IAB_US_GENERAL         | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: true)
        IAB_ALL                | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: true)
        ALL                    | new GppModuleConfig(allowPersonalDataConsent2SnakeCase: true)
    }
}
