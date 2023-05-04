package org.prebid.server.functional.tests.privacy

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

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"

    def "PBS auction call when transmit UFPD activities is allowing requests should leave UFPD fields in request and update proper metrics"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set with generic bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
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
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should remove UFPD fields from request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS auction call when bidder allowed activities have empty condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids[0].source == genericBidRequest.user.eids[0].source
            genericBidderRequest.user.data == genericBidRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == genericBidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS auction call when first rule disallowing in activities should remove UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when transmit UFPD activities is allowing request should leave UFPD fields field in active request and update proper metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call when transmit UFPD activities is rejecting request should remove UFPD fields field in active request and update disallowed metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

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
        def account = getAccountWithAllowActivities(accountId, activities)
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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should remove UFPD fields from request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    def "PBS amp call when bidder allowed activities have empty condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set with have empty condition type"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
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
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

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
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

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
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            genericBidderRequest.user.data == ampStoredRequest.user.data
            genericBidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS amp call when first rule disallowing in activities should remove UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

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
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

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
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext
        }
    }

    private BidRequest generateBidRequestWithAccountAndUfpdData(String accountId) {
        BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
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
            it.user.eids = [Eid.defaultEid]
            it.user.data = [new Data(name: PBSUtils.randomString)]
            it.user.buyeruid = PBSUtils.randomString
            it.user.yob = PBSUtils.randomNumber
            it.user.gender = PBSUtils.randomString
            it.user.ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
        }
    }
}
