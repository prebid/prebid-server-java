package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.ActivityConfig
import org.prebid.server.functional.model.config.EqualityValueRule
import org.prebid.server.functional.model.config.InequalityValueRule
import org.prebid.server.functional.model.config.LogicalRestrictedRule
import org.prebid.server.functional.model.config.GppModuleConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
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

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED
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
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.request.GppSectionId.USP_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_VA_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA

class GppFetchBidActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String ALERT_GENERAL = "alerts.general"

    def "PBS auction call when fetch bid activities is allowing should process bid request and update processed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when fetch bid activities is rejecting should skip call to restricted bidder and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Activities set with all bidders rejected"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS auction call when first rule allowing in activities should call bid adapter"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activity rules"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS auction call when first rule disallowing in activities should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0
    }

    def "PBS auction should process rule when gppSid doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = regsGppSid
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = conditionGppSid
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        regsGppSid        | conditionGppSid
        null              | [USP_V1.intValue]
        [USP_V1.intValue] | null
    }

    def "PBS auction should disallowed rule when gppSid intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_V1.intValue]
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            componentType = null
            componentName = null
            gppSid = [USP_V1.intValue]
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction should process rule when device.geo doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

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
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.regs.gppSid = null
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.value]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
    }

    def "PBS auction should disallowed rule when regs.ext.gpc intersect"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def randomGpc = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = randomGpc
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = randomGpc
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction shouldn't disallowed rule when regs.ext.gpc doesn't intersect"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should disallowed rule when header sec-gpc intersect with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = VALID_VALUE_FOR_GPC_HEADER
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests with headers"
        activityPbsService.sendAuctionRequest(generalBidRequest, ["Sec-GPC": gpcHeader])

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        gpcHeader << [VALID_VALUE_FOR_GPC_HEADER as Integer, VALID_VALUE_FOR_GPC_HEADER]
    }

    def "PBS auction shouldn't disallowed rule when header sec-gpc doesn't intersect with condition"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests with headers"
        activityPbsService.sendAuctionRequest(generalBidRequest, ["Sec-GPC": gpcHeader])

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        gpcHeader << [1, "1"]
    }

    def "PBS auction call when privacy regulation match should call bid adapter"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = new UspNatV1Consent.Builder().build()
        }

        and: "Activities set for fetchBid with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS auction call when request have different gpp consent should call bid adapter"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set for fetchBid with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        gppConsent                            | gppSid
        new UspNatV1Consent.Builder().build() | USP_NAT_V1
        new UspCaV1Consent.Builder().build()  | USP_CA_V1
        new UspVaV1Consent.Builder().build()  | USP_VA_V1
        new UspCoV1Consent.Builder().build()  | USP_CO_V1
        new UspUtV1Consent.Builder().build()  | USP_UT_V1
        new UspCtV1Consent.Builder().build()  | USP_CT_V1
    }

    def "PBS auction call when privacy regulation have duplicate should process request and update alerts metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            ext.prebid.trace = VERBOSE
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for fetchBid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(genericBidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when privacy module contain invalid property should respond with an error"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = new UspNatV1Consent.Builder().build()
            it.setAccountId(accountId)
        }

        and: "Activities set for transmitUfpd with rejecting privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleIabAll]))

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

    def "PBS auction call when privacy regulation don't match custom requirement should call to bidder"() {
        given: "Default basic generic BidRequest"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set for fetch bid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], accountLogic), [USP_NAT_V1], false)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS auction call when privacy regulation match custom requirement should ignore call to bidder"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set for fetch bid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], accountLogic), [USP_NAT_V1], false)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(generalBidRequest.id).size() == 0

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

    def "PBS auction call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], restrictedRule), [USP_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "JsonLogic exception: objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when custom privacy regulation with normalizing should ignore call to bidder"() {
        given: "Generic BidRequest with gpp and account setup"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppStateConsent.build()
            setAccountId(accountId)
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([FETCH_BIDS], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

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
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)

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

    def "PBS amp call when bidder allowed in activities should process bid request and proper metrics and update processed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when bidder rejected in activities should skip call to restricted bidders and update disallowed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Reject activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert bidder.getBidderRequests(ampStoredRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert bidder.getBidderRequests(ampStoredRequest.id).size() == 0
    }

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS amp call when first rule allowing in activities should call each bid adapter"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        assert bidder.getBidderRequest(ampStoredRequest.id)
    }

    def "PBS amp call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert bidder.getBidderRequests(ampStoredRequest.id).size() == 0
    }

    def "PBS amp should process rule when header gpc doesn't intersection with condition.gpc"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

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
        def allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": PBSUtils.randomNumber as String])

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp should disallow rule when header gpc intersection with condition.gpc"() {
        given: "Default amp stored request"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Amp request with link to account"
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
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Bidder request should not contain bidRequest from amp request"
        assert bidder.getBidderRequests(ampStoredRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when privacy regulation match should call bid adapter"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = new UspNatV1Consent.Builder().build()
            it.consentType = GPP
        }

        and: "Activities set for fetchBid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

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

        then: "Generic bidder should be called"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS amp call when request have different gpp consent but match should call bid adapter"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for fetchBid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

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

        then: "Generic bidder should be called"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        where:
        gppConsent                                                          | gppSid
        new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build() | USP_NAT_V1
        new UspCaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CA_V1
        new UspVaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_VA_V1
        new UspCoV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CO_V1
        new UspUtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_UT_V1
        new UspCtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CT_V1
    }

    def "PBS amp call when privacy regulation have duplicate should process request and update alerts metrics"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for fetchBid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder should be called"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when privacy module contain invalid property should respond with an error"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = new UspNatV1Consent.Builder().build()
            it.consentType = GPP
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleIabAll]))

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

    def "PBS amp call when privacy regulation don't match custom requirement should call to bidder"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for fetch bid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder should be called"
        assert bidder.getBidderRequests(ampStoredRequest.id)

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS amp call when privacy regulation match custom requirement should ignore call to bidder"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for fetch bid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(ampStoredRequest.id).size()

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

    def "PBS amp call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_CT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.intValue
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], restrictedRule), [USP_NAT_V1], false)
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

    def "PBS amp call when custom privacy regulation with normalizing should ignore call to bidder"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

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
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([FETCH_BIDS], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

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

        then: "Generic bidder should not be called"
        assert !bidder.getBidderRequests(ampStoredRequest.id)

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
