package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_TID
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class GppTransmitTidActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_TID.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_TID.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_TID.metricValue}.disallowed.count"

    def "PBS auction call when bidder allowed in activities should leave tid fields in request and update processed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with generic bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call when bidder rejected in activities should remove tid fields in request and update disallowed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set with bidder allowed"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.imp.ext.tid
            !bidderRequest.source.tid
        }
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

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

    def "PBS auction call when first rule allowing in activities should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == bidRequest.imp.ext.tid
            bidderRequest.source.tid == bidRequest.source.tid
        }
    }

    def "PBS auction call when first rule disallowing in activities should remove tid fields in request"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS amp call when bidder allowed in activities should leave tid fields in request and update processed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when bidder rejected in activities should remove tid fields in request and update disallowed metrics"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Reject activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with default action set to false"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "Activities set for transmit tid with invalid input"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

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
        activityPbsService.sendAmpRequest(ampRequest)

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

    def "PBS auction call when first rule allowing in activities should leave tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequest.imp.ext.tid == ampStoredRequest.imp.ext.tid
            bidderRequest.source.tid == ampStoredRequest.source.tid
        }
    }

    def "PBS amp call when first rule disallowing in activities should remove tid fields in request"() {
        given: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
            imp.first().ext.tid = PBSUtils.randomString
            source = new Source(tid: PBSUtils.randomString)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with conflict setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_TID, activity)

        and: "Existed account with allow activities setup"
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove source.tid and imp.ext.tid"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            !bidderRequest.source.tid
            !bidderRequest.imp.ext.tid
        }
    }
}
