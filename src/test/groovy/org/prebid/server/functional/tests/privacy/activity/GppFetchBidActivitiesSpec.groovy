package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Component
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class GppFetchBidActivitiesSpec extends ActivityBaseSpec {

    static final ActivityType type = ActivityType.FETCH_BIDS

    def "PBS activity call with all bidders allowed in activities should call each bid adapter"() {
        given: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(SITE, accountId, OPENX)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        then: "Openx bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(openxBidRequest.id)

        where:
        conditions                                                                         | isAllowed
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]                  | false
        [new Condition(componentName: new Component(xIn: [GENERIC.value, OPENX.value]))]   | false
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]          | false
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]              | true
        [new Condition(componentName: new Component(xIn: [APPNEXUS.value]))]               | true
        [new Condition(componentName: new Component(notIn: [GENERIC.value, OPENX.value]))] | true
        [new Condition(componentName: new Component(notIn: [GENERIC.value, OPENX.value]))] | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]                | true
    }

    def "PBS activity call with all bidders reject setup in activities should skip call to each restricted bidders"() {
        given: "Activities set with all bidders rejected"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(SITE, accountId, OPENX)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Resolved response should not contain any seatbid for Openx request"
        assert !openxResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        then: "Openx bidder request should be ignored"
        assert !bidder.getBidderRequests(openxBidRequest.id)

        where:
        conditions                                                                         | isAllowed
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]                  | false
        [new Condition(componentName: new Component(xIn: [GENERIC.value, OPENX.value]))]   | false
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]          | false
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]              | true
        [new Condition(componentName: new Component(xIn: [APPNEXUS.value]))]               | true
        [new Condition(componentName: new Component(notIn: [GENERIC.value, OPENX.value]))] | true
        [new Condition(componentName: new Component(notIn: [GENERIC.value, OPENX.value]))] | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]                | true
    }

    def "PBS activity call with specific bidder in activities should respond only with specific bidder"() {
        given: "Activities set with openx bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(SITE, accountId, OPENX)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Openx bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(openxBidRequest.id)

        and: "Resolved response should not contain seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder should not be called due to positive allow in activities"
        assert !bidder.getBidderRequests(generalBidRequest.id)

        where:
        conditions                                                                       | isAllowed
        [new Condition(componentName: Component.baseComponent)]                                    | false
        [new Condition(componentName: new Component(notIn: [GENERIC.value]))]            | true
        [new Condition(componentName: new Component(xIn: [OPENX.value], notIn: [GENERIC.value]),
                componentType: new Component(xIn: [BIDDER.name]))]                       | true
        [new Condition(componentName: new Component(xIn: [GENERIC.value], notIn: [OPENX.value]),
                componentType: new Component(xIn: [BIDDER.name]))]                       | false
        [new Condition(componentName: new Component(xIn: [OPENX.value], notIn: [GENERIC.value]),
                componentType: new Component(xIn: [GENERAL_MODULE.name]))]               | true
    }

    def "PBS activity call with empty activities should be ignored in process"() {
        given: "Activities set with empty configurations"
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        where:
        activity << [
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], true),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], false),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], true),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], false),
                Activity.getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], true),
                Activity.getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], false),
                Activity.getDefaultActivity(null)
        ]
    }

    def "PBS activity call with specific allow hierarchy in activities should call each bid adapter"() {
        given: "Activities set with with Generic bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)

        where:
        rules << [
                [new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)]
        ]
    }

    def "PBS activity call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should not contain any seatbid for Generic request"
        assert !genericResponse.seatbid

        and: "Generic bidder request should be ignored"
        assert !bidder.getBidderRequests(generalBidRequest.id)
    }

    def "PBS activity call with invalid hierarchy in activities should ignore activities and respond with bidder"() {
        given: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid

        and: "Generic bidder should be called due to positive allow in activities"
        assert bidder.getBidderRequest(generalBidRequest.id)
    }

    def "PBS amp call with allowed bidder in activities should allow call to bidder"() {
        given: "Allow activities setup"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]                         | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [RTD_MODULE.name]))]        | false
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | false
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS amp call with reject bidder in activities setup should skip call to restricted bidders"() {
        given: "Reject activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.tagId = PBSUtils.randomString
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert !bidder.getBidderRequest(ampStoredRequest.id)

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]                         | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [RTD_MODULE.name]))]        | true
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | false
    }

    def "PBS amp call with empty activities settings should be ignored in process"() {
        given: "Empty activities setup"
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)

        where:
        activity << [
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], true),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], false),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], true),
                Activity.getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], false),
                Activity.getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], true),
                Activity.getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], false),
                Activity.getDefaultActivity(null)
        ]
    }

    def "PBS amp call with specific allow hierarchy in activities should call each bid adapter"() {
        given: "Activities set with with generic bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should be present"
        def requestId = ampResponse.ext?.debug?.resolvedRequest?.id
        assert bidder.getBidderRequest(requestId)

        where:
        rules << [
                [new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)]
        ]
    }

    def "PBS amp call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        assert !bidder.getBidderRequest(ampStoredRequest.id)
    }

    def "PBS amp call with invalid hierarchy in activities should ignore activities and respond with bidder"() {
        given: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        Account account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = getBidRequestWithAccount(accountId)

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
