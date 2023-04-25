package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private String activityProcessedRulesForAccount
    private String disallowedCountForAccount
    private String activityRulesProcessedCount = 'requests.activity.processedrules.count'
    private String disallowedCountForActivityRule = "requests.activity.${TRANSMIT_UFPD.value}.disallowed.count"
    private String disallowedCountForGenericAdapter = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.value}.disallowed.count"
    private String disallowedCountForOpenxAdapter = "adapter.${OPENX.value}.activity.${TRANSMIT_UFPD.value}.disallowed.count"

    final static Map<String, Object> ufpdData = [
            "ext"     : new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString)),
            "data"    : [new Data(name: PBSUtils.randomString)],
            "buyeruid": PBSUtils.randomString,
            "yob"     : PBSUtils.randomNumber,
            "gender"  : PBSUtils.randomString,
            "eids"    : [Eid.defaultEid],
            "device"  : new Device(os: PBSUtils.randomizeCase("iOS"), devicetype: PBSUtils.randomNumber)
    ]

    def "PBS action call when transmit UFPD activities is allowing requests should leave #ufpdField in active request and provide proper metrics"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${TRANSMIT_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount] + 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule]
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount]
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter]

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(Condition.baseCondition, true),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), true),
                Activity.getActivityWithRules(Condition.getBaseCondition(OPENX.value), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE]), false)
        ]].combinations()
    }

    def "PBS action call when bidder allowed activities have empty condition type should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS action call when transmit UFPD activities is rejecting requests should remove #ufpdField field in active request and provide disallowed metrics"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${TRANSMIT_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should remove #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter] + 1

        and: "Metrics processed across activities should not be updated"
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(Condition.baseCondition , false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), false)
        ]].combinations()
    }

   def "PBS action call when default activity setting off should not remove #ufpdField field"() {
       given: "Default Generic BidRequests with #ufpdField and account id"
       def accountId = PBSUtils.randomString
       def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

       and: "Allow activities setup"
       def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
       def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

       and: "Save account config with allow activities into DB"
       def account = getAccountWithAllowActivities(accountId, activities)
       accountDao.save(account)

       when: "PBS processes auction requests"
       def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

       then: "Generic bidder request should remove #ufpdField field in request"
       def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
       assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

       where:
       ufpdField << ufpdData.keySet()
   }

    def "PBS action call when transmit UFPD activities is allowing specific bidder should remove #ufpdField in specific bidder and provide metrics"() {
        given: "Default Generic and Openx BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)
        def openxBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String, OPENX)

        and: "Activities set with generic bidders rejected"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${TRANSMIT_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)
        def openxResponse = activityPbsService.sendAuctionRequest(openxBidRequest)

        then: "Generic bidder request should remove #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        then: "Openx bidder request should leave #ufpdField field in request"
        def openxBidderRequest = bidder.getBidderRequest(openxResponse.id)
        assert isUfpdFieldPresent(openxBidderRequest, ufpdField as String)

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
        [ufpdField, rules] << [ufpdData.keySet(), [
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                    [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                     ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value))]
        ]].combinations()
    }

    def "PBS action call when transmit UFPD activities is empty should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Empty activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                Activity.getDefaultActivity(rules: []),
                Activity.getDefaultActivity(null, null)]
        ].combinations()
    }

    def "PBS action call when transmit UFPD activities has specific allow hierarchy should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set with with generic bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, rules] << [ufpdData.keySet(), [
                [new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: true),
                 new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [BIDDER]), allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: true),
                 new ActivityRule(priority: DEFAULT, condition: new Condition(componentType: [GENERAL_MODULE]), allow: false)]
        ]].combinations()
    }    
    
    def "PBS action call when higher priority allow hierarchy in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activity rules with higher priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS action call when confuse in allowing on same priority level in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activity rules with same priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS action call when specific reject hierarchy in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should remove #ufpdField in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS action call when transmit UFPD activities has invalid hierarchy should ignore activities and leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, invalidActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when transmit UFPD activities is allowing all requests should leave #ufpdField field in active request and provide proper metrics"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)
        
        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Current value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${TRANSMIT_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount] + 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule]
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount]
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter]

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(Condition.baseCondition, true),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), true),
                Activity.getActivityWithRules(Condition.getBaseCondition(OPENX.value), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE]), false)
        ]].combinations()
    }

    def "PBS amp call when bidder allowed activities have empty condition type should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }
        
        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when transmit UFPD activities is rejecting all requests should remove #ufpdField field in active request and provide disallowed metrics"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = "accounts.${accountId}.activity.processedrules.count"
        disallowedCountForAccount = "accounts.${accountId}.activity.${TRANSMIT_UFPD.value}.disallowed.coun"
        def initialMetrics = activityPbsService.sendCollectedMetricsRequest()

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove #ufpdField in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)

        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[disallowedCountForActivityRule] == initialMetrics[disallowedCountForActivityRule] + 1
        assert metrics[disallowedCountForAccount] == initialMetrics[disallowedCountForAccount] + 1
        assert metrics[disallowedCountForGenericAdapter] == initialMetrics[disallowedCountForGenericAdapter] + 1

        and: "Metrics processed across activities should not be updated"
        assert metrics[activityRulesProcessedCount] == initialMetrics[activityRulesProcessedCount]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(Condition.baseCondition , false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), false)
        ]].combinations()
    }

    def "PBS amp call when default activity setting off should not remove #ufpdField field"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove #ufpdField in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)

        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }
    
    def "PBS amp call when transmit UFPD activities is empty should leave #ufpdField field in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Empty activities setup"
        AllowActivities activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                Activity.getDefaultActivity(rules: []),
                Activity.getDefaultActivity(null, null)]
        ].combinations()
    }

    def "PBS amp call when higher priority allow hierarchy in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
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
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when confuse in allowing on same priority level in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with same priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when transmit UFPD activities has specific reject hierarchy should remove #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)

        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when transmit UFPD activities has invalid hierarchy should ignore activities and leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, invalidActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        def generalBidderRequest = bidder.getBidderRequest(requestId)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    private boolean isUfpdFieldPresent(BidderRequest request, String ufpdField) {
        switch (ufpdField as String) {
            case "device":
                return request.device
            case "data":
                return request.user."$ufpdField" || request.user.ext?.rp?.target?.iab
            case "eids":
                return request.user."$ufpdField" || request.user.ext?.eids
            default:
                request.user."$ufpdField"
        }
    }

    private BidRequest generateBidRequestWithAccountAndUfpdData(String accountId, String ufpdField, BidderName bidder = GENERIC) {
        getBidRequestWithAccount(SITE, accountId, bidder).tap {
            if (ufpdField == "device") {
                it.device = ufpdData."$ufpdField"
            } else {
                it.user = new User("$ufpdField": ufpdData."$ufpdField")
            }
        }
    }
}
