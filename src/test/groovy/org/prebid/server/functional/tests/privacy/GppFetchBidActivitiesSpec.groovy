package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GppFetchBidActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${FETCH_BIDS.metricValue}.disallowed.coun"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${FETCH_BIDS.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_OPENX_ADAPTER = "adapter.${OPENX.value}.activity.${FETCH_BIDS.metricValue}.disallowed.count"

    def "PBS activity call when fetch bid activities is allowing should process bid request and update processed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
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
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | true
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | true
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        Condition.getBaseCondition(OPENX.value)                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE])                                    | false
        new Condition(componentName: [GENERIC.value], componentType: [ANALYTICS])                                     | false
    }

    def "PBS activity call when fetch bid activities is rejecting should skip call to restricted bidder and update disallowed metrics"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders rejected"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
    }

    def "PBS activity call when default activity setting set to false should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for fetch bids with default action set to false"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)
    }

    def "PBS auction call when fetch bid activities with proper condition type only should process bid request"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set with all bidders allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message == ["Invalid condition type param passed"]

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS auction call with specific bidder in activities should respond only with specific bidder"() {
        given: "Generic and Openx bid requests with account connection"
        def accountId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            addImp(Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.openx = Openx.defaultOpenx
            })
        }

        and: "Activities set with openx bidders allowed"
        def activity = Activity.getDefaultActivity(activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Should be requests only for openx bidders"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 1

        and: "Openx bidder should be called due to activities setup"
        assert !bidderRequests.first().imp.first().ext.bidder

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        and: "Metrics for disallowed activities for Openx should stay the same"
        assert !metrics[DISALLOWED_COUNT_FOR_OPENX_ADAPTER]

        where:
        activityRules << [[ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(OPENX.value), true)]]
    }

    def "PBS activity call when first rule allowing in activities should call bid adapter"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS activity call when first rule disallowing in activities should skip call to restricted bidder"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomString
        def generalBidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)
    }

    def "PBS amp call when bidder allowed in activities should process bid request and proper metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

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

        then: "Bidder request should be present"
        assert bidder.getBidderRequest(ampStoredRequest.id)

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

    def "PBS amp call when bidder rejected in activities should skip call to restricted bidders and update disallowed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Reject activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

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
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
    }

    def "PBS amp call when default activity setting set to false should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

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

    def "PBS amp call when fetch bid activities with proper condition type only should process bid request"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(FETCH_BIDS, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
        }

        and: "Activities set for enrich ufpd with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message == ["Invalid condition type param passed"]

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS auction call when first rule allowing in activities should call each bid adapter"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
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
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        assert bidder.getBidderRequest(ampStoredRequest.id)
    }

    def "PBS amp call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
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
        def activities = AllowActivities.getDefaultAllowActivities(ENRICH_UFPD, activity)

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
}
