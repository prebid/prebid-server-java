package org.prebid.server.functional.tests.privacy.activity

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

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.EMPTY
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class GppTransmitUfpdActivitiesSpec extends ActivityBaseSpec {

    final static Map<String, Object> ufpdData = [
            "ext"     : new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString)),
            "data"    : [new Data(name: PBSUtils.randomString)],
            "buyeruid": PBSUtils.randomString,
            "yob"     : PBSUtils.randomNumber,
            "gender"  : PBSUtils.randomString,
            "eids"    : [Eid.defaultEid],
            "device"  : new Device(os: PBSUtils.randomizeCase("iOS"), devicetype: PBSUtils.randomNumber)
    ]

    def "PBS action call when transmit UFPD activities is allowing requests should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules([new Condition(componentType: [BIDDER])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value], componentType: [BIDDER])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])], true)
        ]].combinations()
    }

    def "PBS action call when transmit UFPD activities is rejecting requests should remove #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should remove #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules([new Condition(componentType: [BIDDER])], false),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value])], false),
                Activity.getActivityWithRules([new Condition(componentType: [GENERAL_MODULE])], false),
                Activity.getActivityWithRules([new Condition(componentType: [RTD_MODULE])], true),
                Activity.getActivityWithRules([new Condition(componentName: [APPNEXUS.value])], true),
        ]].combinations()
    }

    def "PBS action call when transmit UFPD activities is allowing specific bidder should remove #ufpdField in specific bidder"() {
        given: "Default Generic and Openx BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)
        def openxBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String, OPENX)

        and: "Activities set for cookie sync with generic bidders rejected"
        def activity = Activity.getDefaultActivity(rules)
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Generic bidder request should remove #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert !isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        then: "Openx bidder request should leave #ufpdField field in request"
        def openxBidderRequest = bidder.getBidderRequest(openxResponse.id)
        assert isUfpdFieldPresent(openxBidderRequest, ufpdField as String)

        where:
        [ufpdField, rules] << [ufpdData.keySet(), [
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false)],
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value), true)],
                [ActivityRule.getDefaultActivityRule(DEFAULT, Condition.baseCondition, false),
                 ActivityRule.getDefaultActivityRule(DEFAULT, Condition.getBaseCondition(OPENX.value))]
        ]].combinations()
    }

    def "PBS action call when transmit UFPD activities is empty should leave #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Empty activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField field in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], true),
                Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], true),
                Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], true),
                Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], false),
                Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], false),
                Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], false),
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
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

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

    def "PBS action call when transmit UFPD activities has specific reject hierarchy should remove #ufpdField in active request"() {
        given: "Default Generic BidRequests with #ufpdField and account id"
        def accountId = PBSUtils.randomString
        def generalBidRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

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

        and: "Activities set for cookie sync with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, invalidActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Generic bidder request should leave #ufpdField in request"
        def generalBidderRequest = bidder.getBidderRequest(genericResponse.id)
        assert isUfpdFieldPresent(generalBidderRequest, ufpdField as String)

        where:
        ufpdField << ufpdData.keySet()
    }

    def "PBS amp call when transmit UFPD activities is allowing all requests should leave #ufpdField field in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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
                Activity.getActivityWithRules([new Condition(componentType: [BIDDER])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value], componentType: [BIDDER])], true),
                Activity.getActivityWithRules([new Condition(componentName: [GENERIC.value], componentType: [GENERAL_MODULE])], true)
        ]].combinations()
    }

    def "PBS amp call when transmit UFPD activities is rejecting all requests should remove #ufpdField field in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Allow activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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
        [ufpdField, activity] << [ufpdData.keySet(), [
                Activity.getActivityWithRules([Condition.baseCondition], false),
                Activity.getActivityWithRules([new Condition(componentType: [BIDDER])], false),
                Activity.getActivityWithRules([new Condition(componentType: [GENERAL_MODULE])], false),
                Activity.getActivityWithRules([new Condition(componentType: [RTD_MODULE])], true),
                Activity.getActivityWithRules([new Condition(componentName: [APPNEXUS.value])], true),
        ]].combinations()
    }

    def "PBS amp call when transmit UFPD activities is empty should leave #ufpdField field in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Empty activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity as Activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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
                Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], true),
                Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], true),
                Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], true),
                Activity.getActivityWithRules([new Condition(componentName: null, componentType: null)], false),
                Activity.getActivityWithRules([new Condition(componentName: [null], componentType: [null])], false),
                Activity.getActivityWithRules([new Condition(componentName: [""], componentType: [EMPTY])], false),
                Activity.getDefaultActivity(rules: []),
                Activity.getDefaultActivity(null, null)]
        ].combinations()
    }

    def "PBS amp call when transmit UFPD activities has specific allow hierarchy should leave #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set for cookie sync with all bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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

    def "PBS amp call when transmit UFPD activities has specific reject hierarchy should remove #ufpdField in active request"() {
        given: "Default Generic BidRequest with #ufpdField field and account id"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = generateBidRequestWithAccountAndUfpdData(accountId, ufpdField as String)

        and: "Activities set for cookie sync with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)

        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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

        and: "Activities set for cookie sync with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, invalidActivity)

        and: "Save account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

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
