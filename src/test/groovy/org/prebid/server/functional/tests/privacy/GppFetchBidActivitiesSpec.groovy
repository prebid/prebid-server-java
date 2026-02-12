package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.config.ActivityConfig
import org.prebid.server.functional.model.config.EqualityValueRule
import org.prebid.server.functional.model.config.GppModuleConfig
import org.prebid.server.functional.model.config.InequalityValueRule
import org.prebid.server.functional.model.config.LogicalRestrictedRule
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.UsNationalV2ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsNationalV2SensitiveData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.v1.UsCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.v1.UsVaV1Consent
import org.prebid.server.functional.util.privacy.gpp.v2.UsNatV2Consent

import java.time.Instant

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED
import static org.prebid.server.functional.model.config.ConfigCase.CAMEL_CASE
import static org.prebid.server.functional.model.config.ConfigCase.KEBAB_CASE
import static org.prebid.server.functional.model.config.ConfigCase.SNAKE_CASE
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
import static org.prebid.server.functional.model.privacy.Metric.ACCOUNT_PROCESSED_RULES_COUNT
import static org.prebid.server.functional.model.privacy.Metric.PROCESSED_ACTIVITY_RULES_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ACCOUNT_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.gpp.GppDataActivity.CONSENT
import static org.prebid.server.functional.model.privacy.gpp.GppDataActivity.NOT_APPLICABLE
import static org.prebid.server.functional.model.privacy.gpp.GppDataActivity.NO_CONSENT
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.US_VA_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.model.response.BidderErrorCode.BAD_INPUT
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.NoBidResponse.UNKNOWN_ERROR
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO

class GppFetchBidActivitiesSpec extends PrivacyBaseSpec {

    def "PBS auction call when fetch bid activities is allowing should process bid request and update processed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.defaultActivity),
                       new AllowActivities().tap { fetchBidsKebabCase = Activity.defaultActivity },
                       new AllowActivities().tap { fetchBidsSnakeCase = Activity.defaultActivity },
        ]
    }

    def "PBS auction call when fetch bid activities is rejecting should skip call to restricted bidder and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where: "Activities fields name in different case"
        activities << [AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])),
                       new AllowActivities().tap { fetchBidsKebabCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
                       new AllowActivities().tap { fetchBidsSnakeCase = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)]) },
        ]
    }

    def "PBS auction call when default activity setting set to false should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

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
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)
    }

    def "PBS auction call when first rule disallowing in activities should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0
    }

    def "PBS auction should process rule when gppSid doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where:
        regsGppSid        | conditionGppSid
        null              | [USP_V1.intValue]
        [USP_V1.intValue] | null
    }

    def "PBS auction should disallowed rule when gppSid intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [USP_V1.intValue]
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Setup activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where:
        condition << [Condition.baseCondition.tap {
            componentType = null
            componentName = null
            gppSid = [USP_V1.intValue]
        }, Condition.baseCondition.tap {
            componentType = null
            componentName = null
            gppSidKebabCase = [USP_V1.intValue]
        }, Condition.baseCondition.tap {
            componentType = null
            componentName = null
            gppSidSnakeCase = [USP_V1.intValue]
        }]
    }

    def "PBS auction should process rule when device.geo doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

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
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.ISOAlpha3]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
    }

    def "PBS auction should disallowed rule when regs.ext.gpc intersect"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext = new RegsExt(gpc: randomGpc)
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
    }

    def "PBS auction shouldn't disallowed rule when regs.ext.gpc doesn't intersect"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext = new RegsExt(gpc: PBSUtils.randomNumber as String)
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
    }

    def "PBS auction should disallowed rule when header sec-gpc intersect with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": gpcHeader])

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where:
        gpcHeader << [VALID_VALUE_FOR_GPC_HEADER as Integer, VALID_VALUE_FOR_GPC_HEADER]
    }

    def "PBS auction shouldn't disallowed rule when header sec-gpc doesn't intersect with condition"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": gpcHeader])

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[ACCOUNT_PROCESSED_RULES_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1

        where:
        gpcHeader << [1, "1"]
    }

    def "PBS auction call when privacy regulation match should call bid adapter"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = new UsNatV1Consent.Builder().build()
        }

        and: "Activities set for fetchBid with rejecting privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulations])
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
        gppConsent                           | gppSid
        new UsNatV1Consent.Builder().build() | US_NAT_V1
        new UsCaV1Consent.Builder().build()  | US_CA_V1
        new UsVaV1Consent.Builder().build()  | US_VA_V1
        new UsCoV1Consent.Builder().build()  | US_CO_V1
        new UsUtV1Consent.Builder().build()  | US_UT_V1
        new UsCtV1Consent.Builder().build()  | US_CT_V1
    }

    def "PBS auction call when request have disallow logic US nat v2 validation should call bid adapter"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            regs.gppSid = [US_NAT_V1.intValue]
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
        gppConsent << [
                new UsNatV2Consent.Builder().setSensitiveDataProcessing(
                        new UsNationalV2SensitiveData(
                                racialEthnicOrigin: CONSENT,
                                religiousBeliefs: CONSENT,
                                healthInfo: CONSENT,
                                orientation: CONSENT,
                                citizenshipStatus: CONSENT,
                                geneticId: CONSENT,
                                biometricId: CONSENT,
                                idNumbers: CONSENT,
                                accountInfo: CONSENT,
                                unionMembership: CONSENT,
                                communicationContents: CONSENT,
                                consumerHealthData: CONSENT,
                                crimeVictim: CONSENT,
                                nationalOrigin: CONSENT,
                                transgenderStatus: CONSENT
                        )),
                new UsNatV2Consent.Builder().setSensitiveDataProcessing(
                        new UsNationalV2SensitiveData(
                                racialEthnicOrigin: NO_CONSENT,
                                religiousBeliefs: NO_CONSENT,
                                healthInfo: NO_CONSENT,
                                orientation: NO_CONSENT,
                                citizenshipStatus: NO_CONSENT,
                                unionMembership: NO_CONSENT,
                                consumerHealthData: NO_CONSENT,
                                nationalOrigin: NO_CONSENT
                        )),
                new UsNatV2Consent.Builder().setSensitiveDataProcessing(
                        new UsNationalV2SensitiveData(
                                geneticId: NO_CONSENT,
                                biometricId: NO_CONSENT,
                                idNumbers: NO_CONSENT,
                                accountInfo: NO_CONSENT,
                                communicationContents: NO_CONSENT,
                                crimeVictim: NO_CONSENT,
                                transgenderStatus: NO_CONSENT
                        )),
                new UsNatV2Consent.Builder().setSensitiveDataProcessing(
                        new UsNationalV2SensitiveData(
                                geneticId: CONSENT,
                                biometricId: CONSENT,
                                idNumbers: CONSENT,
                                accountInfo: CONSENT,
                                communicationContents: CONSENT,
                                crimeVictim: CONSENT,
                                transgenderStatus: CONSENT
                        )),
                new UsNatV2Consent.Builder().setKnownChildSensitiveDataConsents(
                        new UsNationalV2ChildSensitiveData(childFrom16to17: NO_CONSENT))
        ]
    }

    def "PBS auction call when privacy regulation have duplicate should process request and update alerts metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = BidRequest.defaultBidRequest.tap {
            it.setAccountId(accountId)
            ext.prebid.trace = VERBOSE
            regs.gppSid = [US_NAT_V1.intValue]
        }

        and: "Activities set for fetchBid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account with gpp config"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, gppAccountsConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(genericBidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        where:
        gppAccountsConfig << [[new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [US_NAT_V1]), enabled: false),
                               new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)],
                              [new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSidsKebabCase: [US_NAT_V1]), enabled: false),
                               new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSidsKebabCase: []), enabled: true)],
                              [new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSidsSnakeCase: [US_NAT_V1]), enabled: false),
                               new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSidsSnakeCase: []), enabled: true)]]
    }

    def "PBS auction call when privacy module contain invalid property should respond with an error"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = new UsNatV1Consent.Builder().build()
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
        def response = activityPbsService.sendAuctionRequest(genericBidRequest, SC_UNAUTHORIZED)

        then: "Response should contain error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == ["Unauthorized account id: ${accountId}"]
        }
    }

    def "PBS auction call when privacy regulation don't match custom requirement should call to bidder"() {
        given: "Default basic generic BidRequest"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set for fetch bid with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def activityConfig = new ActivityConfig([FETCH_BIDS], LogicalRestrictedRule.generateSingleRestrictedRule(operator, rulesList))
        def accountLogic = GppModuleConfig.getDefaultModuleConfig(activityConfig, [US_NAT_V1], false, caseType)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = accountLogic
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        where:
        gpcValue | operator | caseType   | rulesList
        false    | OR       | CAMEL_CASE | [new EqualityValueRule(GPC, NO_CONSENT)]
        true     | OR       | CAMEL_CASE | [new InequalityValueRule(GPC, NO_CONSENT)]
        true     | AND      | CAMEL_CASE | [new EqualityValueRule(GPC, NO_CONSENT),
                                            new EqualityValueRule(SHARING_NOTICE, NO_CONSENT)]

        false    | OR       | KEBAB_CASE | [new EqualityValueRule(GPC, NO_CONSENT)]
        true     | OR       | KEBAB_CASE | [new InequalityValueRule(GPC, NO_CONSENT)]
        true     | AND      | KEBAB_CASE | [new EqualityValueRule(GPC, NO_CONSENT),
                                            new EqualityValueRule(SHARING_NOTICE, NO_CONSENT)]

        false    | OR       | SNAKE_CASE | [new EqualityValueRule(GPC, NO_CONSENT)]
        true     | OR       | SNAKE_CASE | [new InequalityValueRule(GPC, NO_CONSENT)]
        true     | AND      | SNAKE_CASE | [new EqualityValueRule(GPC, NO_CONSENT),
                                            new EqualityValueRule(SHARING_NOTICE, NO_CONSENT)]
    }

    def "PBS auction call when privacy regulation match custom requirement should ignore call to bidder"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_NAT_V1.intValue]
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
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], accountLogic), [US_NAT_V1], false)
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert bidder.getBidderRequests(bidRequest.id).size() == 0

        where:
        gppConsent                                                            | valueRules
        new UsNatV1Consent.Builder().setPersonalDataConsents(CONSENT).build() | [new EqualityValueRule(PERSONAL_DATA_CONSENTS, CONSENT)]
        new UsNatV1Consent.Builder().setGpc(true).build()                     | [new EqualityValueRule(GPC, NO_CONSENT)]
        new UsNatV1Consent.Builder().setGpc(false).build()                    | [new InequalityValueRule(GPC, NO_CONSENT)]
        new UsNatV1Consent.Builder().setGpc(true).build()                     | [new EqualityValueRule(GPC, NO_CONSENT),
                                                                                 new EqualityValueRule(SHARING_NOTICE, CONSENT)]
        new UsNatV1Consent.Builder().setPersonalDataConsents(CONSENT).build() | [new EqualityValueRule(GPC, NO_CONSENT),
                                                                                 new EqualityValueRule(PERSONAL_DATA_CONSENTS, CONSENT)]
    }

    def "PBS auction call when custom privacy regulation empty and normalize is disabled should process request and emit error log"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [US_NAT_V1.intValue]
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
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], restrictedRule), [US_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(bidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Logs should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "USCustomLogic creation failed: objects must have exactly 1 key defined, found 0").size() == 1
    }

    def "PBS auction call when custom privacy regulation with normalizing should ignore call to bidder"() {
        given: "Generic BidRequest with gpp and account setup"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppStateConsent
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
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(bidRequest.id)

        where:
        gppSid   | equalityValueRules                                                      | gppStateConsent
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | generateSensitiveGpp(US_CA_V1, [idNumbers: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | generateSensitiveGpp(US_CA_V1, [accountInfo: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [geolocation: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | generateSensitiveGpp(US_CA_V1, [racialEthnicOrigin: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | generateSensitiveGpp(US_CA_V1, [communicationContents: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | generateSensitiveGpp(US_CA_V1, [geneticId: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | generateSensitiveGpp(US_CA_V1, [biometricId: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [healthInfo: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [orientation: CONSENT])

        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CA_V1, [NOT_APPLICABLE, NOT_APPLICABLE])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [NO_CONSENT, NO_CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [NO_CONSENT, CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [CONSENT, NO_CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [CONSENT, CONSENT])

        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [NO_CONSENT, NO_CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [NO_CONSENT, CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [CONSENT, NO_CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [CONSENT, CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_VA_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [NO_CONSENT, NO_CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [NO_CONSENT, CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [CONSENT, NO_CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [CONSENT, CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CO_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | generateSensitiveGpp(US_UT_V1, [racialEthnicOrigin: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | generateSensitiveGpp(US_UT_V1, [religiousBeliefs: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [orientation: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | generateSensitiveGpp(US_UT_V1, [citizenshipStatus: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [healthInfo: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | generateSensitiveGpp(US_UT_V1, [geneticId: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | generateSensitiveGpp(US_UT_V1, [biometricId: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [geolocation: CONSENT])

        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [NO_CONSENT, NO_CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [NO_CONSENT, CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [CONSENT, NO_CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [CONSENT, CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_UT_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, NO_CONSENT])
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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
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
        assert metrics[PROCESSED_ACTIVITY_RULES_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
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
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
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
            it.gppSid = US_NAT_V1.value
            it.consentString = new UsNatV1Consent.Builder().build()
            it.consentType = GPP
        }

        and: "Activities set for fetchBid with allowing privacy regulation"
        def rule = new ActivityRule(privacyRegulation: [privacyAllowRegulations])

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
        gppConsent                                                                                                         | gppSid
        new UsNatV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build() | US_NAT_V1
        new UsCaV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build()  | US_CA_V1
        new UsVaV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build()  | US_VA_V1
        new UsCoV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build()  | US_CO_V1
        new UsUtV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build()  | US_UT_V1
        new UsCtV1Consent.Builder().setMspaServiceProviderMode(MspaMode.YES).setMspaOptOutOptionMode(MspaMode.NO).build()  | US_CT_V1
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
            it.gppSid = US_NAT_V1.value
        }

        and: "Activities set for fetchBid with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, Activity.getDefaultActivity([ruleUsGeneric]))

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
            it.gppSid = US_NAT_V1.value
            it.consentString = new UsNatV1Consent.Builder().build()
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
        def response = activityPbsService.sendAmpRequest(ampRequest, SC_UNAUTHORIZED)

        then: "Response should contain error"
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == ["Unauthorized account id: ${accountId}"]
        }
    }

    def "PBS amp call when privacy regulation don't match custom requirement should call to bidder"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def gppConsent = new UsNatV1Consent.Builder().setGpc(gpcValue).build()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.value
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
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NO_CONSENT)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NO_CONSENT)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NO_CONSENT),
                                                                            new EqualityValueRule(SHARING_NOTICE, NO_CONSENT)])
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
            it.gppSid = US_NAT_V1.value
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
        gppConsent                                                            | valueRules
        new UsNatV1Consent.Builder().setPersonalDataConsents(CONSENT).build() | [new EqualityValueRule(PERSONAL_DATA_CONSENTS, CONSENT)]
        new UsNatV1Consent.Builder().setGpc(true).build()                     | [new EqualityValueRule(GPC, NO_CONSENT)]
        new UsNatV1Consent.Builder().setGpc(false).build()                    | [new InequalityValueRule(GPC, NO_CONSENT)]
        new UsNatV1Consent.Builder().setGpc(true).build()                     | [new EqualityValueRule(GPC, NO_CONSENT),
                                                                                 new EqualityValueRule(SHARING_NOTICE, CONSENT)]
        new UsNatV1Consent.Builder().setPersonalDataConsents(CONSENT).build() | [new EqualityValueRule(GPC, NO_CONSENT),
                                                                                 new EqualityValueRule(PERSONAL_DATA_CONSENTS, CONSENT)]
    }

    def "PBS amp call when custom privacy regulation empty and normalize is disabled should process request and emit error log"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UsNatV1Consent.Builder().setGpc(true).build()
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            regs.gppSid = [US_CT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = US_NAT_V1.intValue
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
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([FETCH_BIDS], restrictedRule), [US_NAT_V1], false)
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

        then: "Generic bidder should be called"
        assert bidder.getBidderRequests(ampStoredRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Logs should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "USCustomLogic creation failed: objects must have exactly 1 key defined, found 0").size() == 1
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
            it.consentString = gppStateConsent
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
        gppSid   | equalityValueRules                                                      | gppStateConsent
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | generateSensitiveGpp(US_CA_V1, [idNumbers: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | generateSensitiveGpp(US_CA_V1, [accountInfo: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [geolocation: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | generateSensitiveGpp(US_CA_V1, [racialEthnicOrigin: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | generateSensitiveGpp(US_CA_V1, [communicationContents: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | generateSensitiveGpp(US_CA_V1, [geneticId: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | generateSensitiveGpp(US_CA_V1, [biometricId: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [healthInfo: CONSENT])
        US_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | generateSensitiveGpp(US_CA_V1, [orientation: CONSENT])

        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CA_V1, [NOT_APPLICABLE, NOT_APPLICABLE])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [NO_CONSENT, NO_CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [NO_CONSENT, CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [CONSENT, NO_CONSENT])
        US_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CA_V1, [CONSENT, CONSENT])

        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [NO_CONSENT, NO_CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [NO_CONSENT, CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [CONSENT, NO_CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_VA_V1, [CONSENT, CONSENT])
        US_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_VA_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [NO_CONSENT, NO_CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [NO_CONSENT, CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [CONSENT, NO_CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CO_V1, [CONSENT, CONSENT])
        US_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CO_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | generateSensitiveGpp(US_UT_V1, [racialEthnicOrigin: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | generateSensitiveGpp(US_UT_V1, [religiousBeliefs: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [orientation: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | generateSensitiveGpp(US_UT_V1, [citizenshipStatus: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [healthInfo: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | generateSensitiveGpp(US_UT_V1, [geneticId: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | generateSensitiveGpp(US_UT_V1, [biometricId: CONSENT])
        US_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | generateSensitiveGpp(US_UT_V1, [geolocation: CONSENT])

        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [NO_CONSENT, NO_CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [NO_CONSENT, CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [CONSENT, NO_CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_UT_V1, [CONSENT, CONSENT])
        US_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_UT_V1, [NOT_APPLICABLE, NOT_APPLICABLE])

        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NOT_APPLICABLE, CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [NO_CONSENT, CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NOT_APPLICABLE, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, NO_CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, NO_CONSENT, CONSENT])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, NOT_APPLICABLE])
        US_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                    new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | generateChildSensitiveGpp(US_CT_V1, [CONSENT, CONSENT, NO_CONSENT])
    }
}
