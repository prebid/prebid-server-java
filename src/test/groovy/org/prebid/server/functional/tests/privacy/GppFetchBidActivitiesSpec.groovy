package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class GppFetchBidActivitiesSpec extends PrivacyBaseSpec {

    private def activityProcessedRulesForAccount = "accounts.%s.activity.processedrules.count"
    private def disallowedCountForAccount = "accounts.%s.activity.${FETCH_BIDS.metricValue}.disallowed.coun"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_OPENX_ADAPTER = "adapter.${OPENX.value}.activity.${FETCH_BIDS.metricValue}.disallowed.count"

    def "PBS activity call with bidder allowed in activities should process bid request and proper metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert !metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE]
        assert !metrics[disallowedCountForAccount]
        assert !metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER]

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | true
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        Condition.getBaseCondition(OPENX.value)                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])                                    | false
        new Condition(componentName: [GENERIC.value], componentType: [ANALYTICS])                                     | false
    }

    def "PBS activity call with bidder allowed activities have empty condition type should process bid request"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set for fetch bids with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS activity call with bidders reject setup in activities should skip call to restricted bidders and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set with all bidders rejected"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[disallowedCountForAccount] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        and: "Metrics processed across activities should not be updated"
        assert !metrics[ACTIVITY_RULES_PROCESSED_COUNT]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | false
    }

    def "PBS activity call with default activity setting off should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[disallowedCountForAccount] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        and: "Metrics processed across activities should not be updated"
        assert !metrics[ACTIVITY_RULES_PROCESSED_COUNT]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | false
    }

    def "PBS activity call with specific bidder in activities should respond only with specific bidder"() {
        given: "Generic and Openx bid requests with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)
        def openxBidRequest = getBidRequestWithAccount(SITE, accountId, OPENX)

        and: "Activities set with openx bidders allowed"
        def activity = Activity.getDefaultActivity(true, activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)
        def openxResponse = activityPbsService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Openx bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(openxBidRequest.id)

        and: "Resolved response should not contain seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder should not be called due to positive allow in activities"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[disallowedCountForAccount] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        and: "Metrics for disallowed activities for Openx should stay the same"
        assert !metrics[DISALLOWED_COUNT_FOR_OPENX_ADAPTER]

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value), true)]]
    }

    def "PBS activity call with empty activities should be ignored in process"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS activity call with higher priority allow hierarchy in activities should call bid adapter"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS activity call with confuse in allowing on same priority level should call bid adapter"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activity rules with same priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS activity call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)
    }

    def "PBS activity call with invalid hierarchy in activities should ignore activities priority and skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, invalidActivity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)
    }

    def "PBS amp call with bidder allowed in activities should process bid request and proper metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[activityProcessedRulesForAccount] == 1

        and: "Metrics for disallowed activities should not be updated"
        assert !metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE]
        assert !metrics[disallowedCountForAccount]
        assert !metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER]

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | true
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        Condition.getBaseCondition(OPENX.value)                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])                                    | false
        new Condition(componentName: [GENERIC.value], componentType: [ANALYTICS])                                     | false
    }

    def "PBS amp call with bidder allowed activities have empty condition type should process bid request"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for fetch bids with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)
    }

    def "PBS amp call with bidders reject setup in activities should skip call to restricted bidders and update disallowed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Reject activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Initial value of metrics"
        activityProcessedRulesForAccount = activityProcessedRulesForAccount.formatted(accountId)
        disallowedCountForAccount = disallowedCountForAccount.formatted(accountId)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert !bidder.getBidderRequest(ampStoredRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[disallowedCountForAccount] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        and: "Metrics processed across activities should not be updated"
        assert !metrics[ACTIVITY_RULES_PROCESSED_COUNT]
        assert !metrics[activityProcessedRulesForAccount]

        where:
        conditions                                                                     | isAllowed
        Condition.baseCondition                                                        | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]) | false
    }

    def "PBS amp call with default activity setting off should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert !bidder.getBidderRequest(ampStoredRequest.id)
    }

    def "PBS amp call with empty activities settings should be ignored in process"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Empty activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)

        where:
        activity << [Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                     Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                     Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                     Activity.getDefaultActivity(rules: []),
                     Activity.getDefaultActivity(null, null)]
    }

    def "PBS amp call with higher priority allow hierarchy in activities should call each bid adapter"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)
    }

    def "PBS amp call with confuse in allowing on same priority level should call each bid adapter"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with different priority"
        def topPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: true)

        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT,
                condition: Condition.baseCondition,
                allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)
    }

    def "PBS amp call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert !bidder.getBidderRequest(ampStoredRequest.id)
    }

    def "PBS amp call with invalid hierarchy in activities should ignore activities and respond with bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, invalidActivity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)
    }
}
