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
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GppTransmitUfpdActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_UFPD.metricValue}.disallowed.count"

    def "PBS auction call when transmit UFPD activities is allowing requests should leave UFPD fields in request and update proper metrics"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set with generic bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

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
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
        }

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

    def "PBS auction call when transmit UFPD activities is rejecting requests should remove UFPD fields in request and update disallowed metrics"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
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
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
    }

    def "PBS auction call when default activity setting set to false should remove UFPD fields from request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have empty UFPD fields"
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
    }

    def "PBS auction call when TransmitUfpd activities with proper condition type only should leave UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set with generic bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
        }

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS auction call when bidder allowed activities have empty condition type should leave UFPD fields in request"() {
        given: "Default Generic BidRequests with UFPD fields and account id"
        def accountId = PBSUtils.randomString
        def genericBidRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "Activities set for transmit ufpd with bidder allowed without type"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendAuctionRequest(genericBidRequest)

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

    def "PBS auction call when transmit UFPD activities is allowing specific bidder should remove UFPD fields in specific bidder and provide metrics"() {
        given: "BidRequests with Generic and Openx imps, UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def bidRequest = generateBidRequestWithAccountAndUfpdData(accountId).tap {
            addImp(Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.openx = Openx.defaultOpenx
            }
            )
        }

        and: "Activities set with generic bidders rejected"
        def activity = Activity.getDefaultActivity(activityRules)
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

        def openxBidderRequest = bidderRequests.find { !it.imp.first().ext.bidder }
        def genericBidderRequest = bidderRequests.find { it.imp.first().ext.bidder }

        and: "Openx bidder request should have data in UFPD fields"
        verifyAll {
            openxBidderRequest.device
            openxBidderRequest.user.buyeruid
            openxBidderRequest.user.yob
            openxBidderRequest.user.gender
            openxBidderRequest.user.eids
            openxBidderRequest.user.data
        }

        then: "Generic bidder request should have empty UFPD fields"
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
        activityRules << [[ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)],
                          [ActivityRule.getDefaultActivityRule(Condition.baseCondition, false),
                           ActivityRule.getDefaultActivityRule(Condition.getBaseCondition(OPENX.value), true)]]
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
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
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
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
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
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have data in UFPD fields"
        def genericBidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
        }

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

    def "PBS amp call when transmit UFPD activities is rejecting request should remove UFPD fields field in active request and update disallowed metrics"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
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
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Generic bidder request should have empty UFPD fields"
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
        conditions                                                                                                    | isAllowed
        Condition.baseCondition                                                                                       | false
        new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])                                | false
        new Condition(componentName: [GENERIC.value], componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | false
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

        then: "Generic bidder request should have empty UFPD fields"
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
    }

    def "PBS amp call when TransmitUfpd activities with proper condition type only should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set only with proper type and empty name"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
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
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
        }

        where:
        conditions                                                                    | isAllowed
        new Condition(componentName: [], componentType: [BIDDER])                     | true
        new Condition(componentType: [BIDDER])                                        | true
        new Condition(componentType: [GENERAL_MODULE])                                | true
        new Condition(componentType: [BIDDER, GENERAL_MODULE, RTD_MODULE, ANALYTICS]) | true
        new Condition(componentType: [RTD_MODULE])                                    | false
        new Condition(componentType: [ANALYTICS])                                     | false
    }

    def "PBS amp call when bidder allowed activities have empty condition type should leave UFPD fields in request"() {
        given: "Default Generic BidRequest with UFPD fields field and account id"
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
            genericBidderRequest.device
            genericBidderRequest.user.buyeruid
            genericBidderRequest.user.yob
            genericBidderRequest.user.gender
            genericBidderRequest.user.eids
            genericBidderRequest.user.data
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
            !genericBidderRequest.device
            !genericBidderRequest.user.buyeruid
            !genericBidderRequest.user.yob
            !genericBidderRequest.user.gender
            !genericBidderRequest.user.eids
            !genericBidderRequest.user.ext?.eids
            !genericBidderRequest.user.data
            !genericBidderRequest.user.ext?.rp?.target?.iab
        }
    }

    private BidRequest generateBidRequestWithAccountAndUfpdData(String accountId) {
        BidRequest.getDefaultBidRequest().tap {
            setAccountId(accountId)
            it.device = new Device(os: PBSUtils.randomizeCase("iOS"), devicetype: PBSUtils.randomNumber)
            user = User.defaultUser
            it.user.eids = [Eid.defaultEid]
            it.user.data = [new Data(name: PBSUtils.randomString)]
            it.user.buyeruid = PBSUtils.randomString
            it.user.yob = PBSUtils.randomNumber
            it.user.gender = PBSUtils.randomString
            it.user.ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
        }
    }
}
