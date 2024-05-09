package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.ActivityConfig
import org.prebid.server.functional.model.config.EqualityValueRule
import org.prebid.server.functional.model.config.GppModuleConfig
import org.prebid.server.functional.model.config.InequalityValueRule
import org.prebid.server.functional.model.config.LogicalRestrictedRule
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
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
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.UsCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UsVaV1Consent
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData

import java.time.Instant

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED
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
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_VA_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String ALERT_GENERAL = "alerts.general"

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
            !genericBidderRequest.user.data
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids
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

        and: "Save account config with allow activities into DB"
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids
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
            !genericBidderRequest.user.data
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

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
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        deviceGeo                                           | conditionGeo
        null                                                | [USA.ISOAlpha3]
        new Geo(country: USA)                               | null
        new Geo(region: ALABAMA.abbreviation)               | [USA.withState(ALABAMA)]
        new Geo(country: CAN, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
    }

    def "PBS auction should disallowed rule when device.geo intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            it.setAccountId(accountId)
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
            !bidderRequest.user.data
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert bidderRequest.user.eids == bidRequest.user.eids

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.ISOAlpha3]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
    }

    def "PBS auction should process rule when regs.ext.gpc doesn't intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
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
            !bidderRequest.user.data
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert bidderRequest.user.eids == bidRequest.user.eids

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
            !bidderRequest.user.data
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert bidderRequest.user.eids == bidRequest.user.eids

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when privacy regulation match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS auction call when privacy module contain some part of disallow logic should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = disallowGppLogic
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UsNatV1Consent.Builder()
                        .setMspaServiceProviderMode(1)
                        .setMspaOptOutOptionMode(2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSaleOptOutNotice(2)
                        .setSaleOptOut(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
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
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(2)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(0, 1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(0, 2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(1, 0)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setPersonalDataConsents(2)
                        .build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        racialEthnicOrigin: 1,
                        religiousBeliefs: 1,
                        healthInfo: 1,
                        orientation: 1,
                        citizenshipStatus: 1,
                        unionMembership: 1,
                )).build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                racialEthnicOrigin: 2,
                                religiousBeliefs: 2,
                                healthInfo: 2,
                                orientation: 2,
                                citizenshipStatus: 2,
                                geneticId: 2,
                                biometricId: 2,
                                idNumbers: 2,
                                accountInfo: 2,
                                unionMembership: 2,
                                communicationContents: 2
                        )).build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                racialEthnicOrigin: 2,
                                religiousBeliefs: 2,
                                healthInfo: 2,
                                orientation: 2,
                                citizenshipStatus: 2,
                                geneticId: 2,
                                biometricId: 2,
                                idNumbers: 2,
                                accountInfo: 2,
                                unionMembership: 2,
                                communicationContents: 2
                        )).build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        geneticId: 1,
                        biometricId: 1,
                        idNumbers: 1,
                        accountInfo: 1,
                        communicationContents: 1
                )).build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        geneticId: 2,
                        biometricId: 2,
                        idNumbers: 2,
                        accountInfo: 2,
                        communicationContents: 2
                )).build()
        ]
    }

    def "PBS auction call when privacy module contain some part of disallow logic which violates GPP validation should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = disallowGppLogic
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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

        where:
        disallowGppLogic << [
                'DBABLA~BAAgAAAAAAA.QA',
                'DBABLA~BCAAAAAAAAA.QA',
                'DBABLA~BAAEAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA'
        ]
    }

    def "PBS auction call when request have different gpp consent but match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

        where:
        gppConsent                                                                                    | gppSid
        new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build() | US_NAT_V1
        new UsCaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CA_V1
        new UsVaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_VA_V1
        new UsCoV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CO_V1
        new UsUtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_UT_V1
        new UsCtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CT_V1
    }

    def "PBS auction call when privacy modules contain allowing settings should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

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
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        where:
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: true)
        ]
    }

    def "PBS auction call when regs.gpp in request is allowing should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = regsGpp
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        where:
        regsGpp << ["", new UsNatV1Consent.Builder().build(), new UsNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS auction call when privacy regulation have duplicate should leave UFPD fields in request and update alerts metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
        }

        and: "Activities set for transmitUfpd with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
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
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when privacy module contain invalid property should respond with an error"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == UNAUTHORIZED.code()
        assert error.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS auction call when privacy regulation don't match custom requirement should leave UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS auction call when privacy regulation match custom requirement should remove UFPD fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == generalBidRequest.user.eids

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

    def "PBS auction call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [US_CT_V1.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], restrictedRule), [US_CT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "JsonLogic exception: objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when custom privacy regulation with normalizing that match custom config should have empty UFPD fields"() {
        given: "Generic BidRequest with gpp and account setup"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId).tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppStateConsent.build()
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([TRANSMIT_UFPD], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with enabled normalizeFlag"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Existed account with gpp regulation setup"
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

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
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when privacy regulation match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS amp call when privacy module contain some part of disallow logic should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = disallowGppLogic
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UsNatV1Consent.Builder()
                        .setMspaServiceProviderMode(1)
                        .setMspaOptOutOptionMode(2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSaleOptOut(1)
                        .setSaleOptOutNotice(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSaleOptOutNotice(2)
                        .setSaleOptOut(1)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
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
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(2)
                        .setMspaServiceProviderMode(2)
                        .setMspaOptOutOptionMode(1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(0, 1)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(0, 2)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setKnownChildSensitiveDataConsents(1, 0)
                        .build(),
                new UsNatV1Consent.Builder()
                        .setPersonalDataConsents(2)
                        .build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        racialEthnicOrigin: 1,
                        religiousBeliefs: 1,
                        healthInfo: 1,
                        orientation: 1,
                        citizenshipStatus: 1,
                        unionMembership: 1,
                )).build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                racialEthnicOrigin: 2,
                                religiousBeliefs: 2,
                                healthInfo: 2,
                                orientation: 2,
                                citizenshipStatus: 2,
                                geneticId: 2,
                                biometricId: 2,
                                idNumbers: 2,
                                accountInfo: 2,
                                unionMembership: 2,
                                communicationContents: 2
                        )).build(),
                new UsNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                racialEthnicOrigin: 2,
                                religiousBeliefs: 2,
                                healthInfo: 2,
                                orientation: 2,
                                citizenshipStatus: 2,
                                geneticId: 2,
                                biometricId: 2,
                                idNumbers: 2,
                                accountInfo: 2,
                                unionMembership: 2,
                                communicationContents: 2
                        )).build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        geneticId: 1,
                        biometricId: 1,
                        idNumbers: 1,
                        accountInfo: 1,
                        communicationContents: 1
                )).build(),
                new UsNatV1Consent.Builder().setSensitiveDataProcessing(new UsNationalSensitiveData(
                        geneticId: 2,
                        biometricId: 2,
                        idNumbers: 2,
                        accountInfo: 2,
                        communicationContents: 2
                )).build()
        ]
    }

    def "PBS amp call when privacy module contain some part of disallow logic which violates GPP validation should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = disallowGppLogic
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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

        where:
        disallowGppLogic << [
                'DBABLA~BAAgAAAAAAA.QA',
                'DBABLA~BCAAAAAAAAA.QA',
                'DBABLA~BAAEAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA',
                'DBABLA~BAAIAAAAAAA.QA'
            ]
    }

    def "PBS amp call when request have different gpp consent but match and rejecting should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

        where:
        gppConsent                                                                                    | gppSid
        new UsNatV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build() | US_NAT_V1
        new UsCaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CA_V1
        new UsVaV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_VA_V1
        new UsCoV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CO_V1
        new UsUtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_UT_V1
        new UsCtV1Consent.Builder().setMspaServiceProviderMode(1).setMspaOptOutOptionMode(2).build()  | US_CT_V1
    }

    def "PBS amp call when privacy modules contain allowing settings should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        where:
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: true)
        ]
    }

    def "PBS amp call when regs.gpp in request is allowing should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = regsGpp
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        where:
        regsGpp << ["", new UsNatV1Consent.Builder().build(), new UsNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS amp call when privacy regulation have duplicate should leave UFPD fields in request and update alerts metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = ""
            it.consentType = GPP
        }

        and: "Activities set for transmitUfpd with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
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
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when privacy module contain invalid property should respond with an error"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == UNAUTHORIZED.code()
        assert error.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS amp call when privacy regulation don't match custom requirement should leave UFPD fields in request"() {
        given: "Store bid request with link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account and gpp"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
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
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS amp call when privacy regulation match custom requirement should remove UFPD fields from request"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmit ufpd with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

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

    def "PBS amp call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Store bid request with link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account and gpp string"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.intValue
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_UFPD], restrictedRule), [US_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp requests"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "Invalid account configuration: JsonLogic exception: " +
                "objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when custom privacy regulation with normalizing should change request consent and call to bidder"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.intValue
            it.consentString = gppStateConsent.build()
            it.consentType = GPP
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([TRANSMIT_UFPD], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with enabled normalizeFlag"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp requests"
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
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Generic bidder request should have data in EIDS fields"
        assert genericBidderRequest.user.eids == ampStoredRequest.user.eids

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

    def "PBS auction call when transmit UFPD activities is rejecting requests with activityTransition false should remove only UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = givenBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities).tap {
            it.config.privacy.gdpr = new AccountGdprConfig(purposes: [(Purpose.P4): new PurposeConfig(eid: new PurposeEid(activityTransition: false))])
        }
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
            !genericBidderRequest.user.data
        }

        and: "Eids fields should have original data"
        assert genericBidderRequest.user.eids == genericBidRequest.user.eids

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    private static BidRequest givenBidRequestWithAccountAndUfpdData(String accountId) {
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
            it.user.customdata = PBSUtils.randomString
            it.user.eids = [Eid.defaultEid]
            it.user.data = [new Data(name: PBSUtils.randomString)]
            it.user.buyeruid = PBSUtils.randomString
            it.user.yob = PBSUtils.randomNumber
            it.user.gender = PBSUtils.randomString
            it.user.ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
        }
    }
}
