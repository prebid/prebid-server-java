package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.UfpdCategories
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.UfpdCategories.DEVICE_SPECIFIC_IDS
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_BUYERUID
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_DATA
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_EIDS
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_EXT_DATA
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_GENDER
import static org.prebid.server.functional.model.request.auction.UfpdCategories.USER_YOB

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${TRANSMIT_UFPD.value}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_UFPD.value}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.value}.disallowed.count"

    def "PBS auction call when transmit UFPD activities is allowing requests should leave #ufpdField in active request and provide proper metrics"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == genericBidRequest.user.eids
            genericBidderRequest.user.data == genericBidRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
                Activity.getActivityWithRules(Condition.baseCondition, true),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), true),
                Activity.getActivityWithRules(Condition.getBaseCondition(OPENX.value), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE]), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]), true)
        ]].combinations()
    }

    def "PBS auction call when bidder allowed activities have empty condition type should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: null), true)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == genericBidRequest.user.eids
            genericBidderRequest.user.data == genericBidRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS auction call when transmit UFPD activities is rejecting requests should remove #ufpdField field in active request and provide disallowed metrics"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should remove UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
                Activity.getActivityWithRules(Condition.baseCondition, false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]), false)
        ]].combinations()
    }

    def "PBS auction call when default activity setting off should not remove #ufpdField field"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should remove UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS auction call when transmit UFPD activities is allowing specific bidder should remove #ufpdField in specific bidder and provide metrics"() {
        given: "BidRequests with Generic and Openx imps, #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def bidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories).tap {
            imp += Imp.getDefaultImpression(MediaType.VIDEO).tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.openx = Openx.defaultOpenx
            }
        }

        and: "Activities set with generic bidders rejected"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Should be requests for generic and openx bidders"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests.size() == 2

        def genericBidderRequest = bidderRequests.find { it.imp.first().ext.bidder }
        def openxBidderRequest = bidderRequests.find { !it.imp.first().ext.bidder }

        and: "Generic bidder request should leave UFPD fields in request as was in original"
        verifyAll {
            openxBidderRequest.device == bidRequest.device
            openxBidderRequest.user.buyeruid == bidRequest.user.buyeruid
            openxBidderRequest.user.yob == bidRequest.user.yob
            openxBidderRequest.user.gender == bidRequest.user.gender
            openxBidderRequest.user.eids ?: openxBidderRequest.user.ext?.eids == bidRequest.user.eids
            openxBidderRequest.user.data == bidRequest.user.data || openxBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Generic bidder request should remove all UFPD field in request"
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        and: "Metrics for disallowed activities should be updated for activity rule and account"
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        [ufpdField, rules] << [UfpdCategories.values(), [
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]), false),
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                 ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value))]
        ]].combinations()
    }

    def "PBS auction call when transmit UFPD activities is empty should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Empty activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), true),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), true),
                Activity.getActivityWithRules(new Condition(componentName: null, componentType: null), false),
                Activity.getActivityWithRules(new Condition(componentName: [null], componentType: [null]), false),
                Activity.getDefaultActivity(rules: []),
                Activity.getDefaultActivity(null, null)]
        ].combinations()
    }

    def "PBS auction call when higher priority allow hierarchy in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == genericBidRequest.user.eids
            genericBidderRequest.user.data == genericBidRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS auction call when confuse in allowing on same priority level in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == genericBidRequest.user.eids
            genericBidderRequest.user.data == genericBidRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS auction call when specific reject hierarchy in transmit UFPD activities should remove #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should remove UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS auction call when transmit UFPD activities has invalid hierarchy should ignore activities and leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "Activities set with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, invalidActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)
        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid == genericBidRequest.user.buyeruid
            genericBidderRequest.user.yob == genericBidRequest.user.yob
            genericBidderRequest.user.gender == genericBidRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == genericBidRequest.user.eids
            genericBidderRequest.user.data == genericBidRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when transmit UFPD activities is allowing all requests should leave #ufpdField field in active request and provide proper metrics"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
                Activity.getActivityWithRules(Condition.baseCondition, true),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), true),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]), true),
                Activity.getActivityWithRules(Condition.getBaseCondition(OPENX.value), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [RTD_MODULE]), false)
        ]].combinations()
    }

    def "PBS amp call when bidder allowed activities have empty condition type should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when transmit UFPD activities is rejecting all requests should remove #ufpdField field in active request and provide disallowed metrics"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
                Activity.getActivityWithRules(Condition.baseCondition, false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE]), false),
                Activity.getActivityWithRules(new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]), false)
        ]].combinations()
    }

    def "PBS amp call when default activity setting off should not remove #ufpdField field"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove UFPD fields in request"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when transmit UFPD activities is empty should leave #ufpdField field in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Empty activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        [ufpdField, activity] << [UfpdCategories.values(), [
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
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when confuse in allowing on same priority level in transmit UFPD activities should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when transmit UFPD activities has specific reject hierarchy should remove #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should remove UFPD fields field in request"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    def "PBS amp call when transmit UFPD activities has invalid hierarchy should ignore activities and leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as UfpdCategories)

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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should leave UFPD fields in request as was in original"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll {
            genericBidderRequest.device == ampStoredRequest.device
            genericBidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            genericBidderRequest.user.yob == ampStoredRequest.user.yob
            genericBidderRequest.user.gender == ampStoredRequest.user.gender
            genericBidderRequest.user.eids ?: genericBidderRequest.user.ext?.eids == ampStoredRequest.user.eids
            genericBidderRequest.user.data == ampStoredRequest.user.data || genericBidderRequest.user.ext?.rp?.target?.iab
        }

        where:
        ufpdField << UfpdCategories.values()
    }

    private BidRequest generateBidRequestWithAccountAndUfpdData(String accountId, UfpdCategories requiredField) {
        BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId)
            user = User.defaultUser
            switch (requiredField) {
                case DEVICE_SPECIFIC_IDS:
                    return it.device = new Device(os: PBSUtils.randomizeCase("iOS"), devicetype: PBSUtils.randomNumber)
                case USER_EIDS:
                    return it.user.eids = [Eid.defaultEid]
                case USER_DATA:
                    return it.user.data = [new Data(name: PBSUtils.randomString)]
                case USER_BUYERUID:
                    return it.user.buyeruid = PBSUtils.randomString
                case USER_YOB:
                    return it.user.yob = PBSUtils.randomNumber
                case USER_GENDER:
                    return it.user.gender = PBSUtils.randomString
                case USER_EXT_DATA:
                    return it.user.ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
                default: return it
            }
        }
    }
}
