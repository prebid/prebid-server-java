package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.activitie.Activity
import org.prebid.server.functional.model.request.activitie.ActivityRule
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.activitie.Component
import org.prebid.server.functional.model.request.activitie.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.activitie.Activity.getActivityWithRules
import static org.prebid.server.functional.model.request.activitie.Activity.getDefaultActivity
import static org.prebid.server.functional.model.request.activitie.ActivityRule.Priory.DEFAULT
import static org.prebid.server.functional.model.request.activitie.ActivityRule.Priory.INVALID
import static org.prebid.server.functional.model.request.activitie.ActivityRule.Priory.TOP
import static org.prebid.server.functional.model.request.activitie.AllowActivities.getDefaultAllowActivities
import static org.prebid.server.functional.model.request.activitie.Component.baseComponent
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.activitie.Condition.getBaseCondition
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.util.PBSUtils.randomNumber

class GppFetchBidActivitiesSpec extends ActivityBaseSpec {

    static final AllowActivities.ActivityType type = AllowActivities.ActivityType.FETCH_BID

    def "PBS activity call with all bidders allowed in activities should call each bid adapter"() {
        given: "Activities set with all bidders allowed"
        def activity = getActivityWithRules(conditions, isAllowed)
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        def activity = getActivityWithRules(conditions, isAllowed)
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        def activity = getActivityWithRules(conditions, isAllowed)
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        [new Condition(componentName: baseComponent)]                                    | false
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
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
                getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], true),
                getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], false),
                getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], true),
                getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], false),
                getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], true),
                getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], false),
                getDefaultActivity(null)
        ]
    }

    def "PBS activity call with specific allow hierarchy in activities should call each bid adapter"() {
        given: "Activities set with with Generic bidders allowed by hierarchy config"
        def activity = getDefaultActivity(rules)
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
                [new ActivityRule(priority: TOP, condition: baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: false)]
        ]
    }

    def "PBS activity call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: TOP, condition: baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: true)
        def activity = getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        def invalidRule = new ActivityRule(priority: INVALID, condition: baseCondition, allow: false)
        def invalidActivity = getDefaultActivity([invalidRule])
        def activities = getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        Activity activity = getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = randomNumber as String
        def account = getDefaultAccount(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = accountId
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
        [new Condition(componentName: baseComponent)]                         | true
        [new Condition(componentName: baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | false
        [new Condition(componentName: baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | true
        [new Condition(componentName: baseComponent,
                componentType: new Component(xIn: [RTD_MODULE.name]))]        | false
        [new Condition(componentName: baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | false
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS amp call with reject bidder in activities setup should skip call to restricted bidders"() {
        given: "Reject activities setup"
        Activity activity = getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = randomNumber as String
        def account = getDefaultAccount(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
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
        [new Condition(componentName: baseComponent)]                         | false
        [new Condition(componentName: baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | true
        [new Condition(componentName: baseComponent,
                componentType: new Component(xIn: [RTD_MODULE.name]))]        | true
        [new Condition(componentName: baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | false
    }

    def "PBS amp call with empty activities settings should be ignored in process"() {
        given: "Empty activities setup"
        AllowActivities allowSetup = getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = randomNumber as String
        def account = getDefaultAccount(accountId, allowSetup)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            account = accountId
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
                getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], true),
                getActivityWithRules([new Condition(componentName: new Component(xIn: [""], notIn: [""]))], false),
                getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], true),
                getActivityWithRules([new Condition(componentName: new Component(xIn: null, notIn: null))], false),
                getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], true),
                getActivityWithRules([new Condition(componentType: new Component(xIn: [""], notIn: [""]))], false),
                getDefaultActivity(null)
        ]
    }

    def "PBS amp call with specific allow hierarchy in activities should call each bid adapter"() {
        given: "Activities set with with generic bidders allowed by hierarchy config"
        def activity = getDefaultActivity(rules)
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
                [new ActivityRule(priority: TOP, condition: baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: false)]
        ]
    }

    def "PBS amp call with specific reject hierarchy in activities should skip call to restricted bidder"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: TOP, condition: baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: baseCondition, allow: true)
        def activity = getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
        def invalidRule = new ActivityRule(priority: INVALID, condition: baseCondition, allow: false)
        def invalidActivity = getDefaultActivity([invalidRule])
        def activities = getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = randomNumber as String
        Account account = getDefaultAccount(accountId, activities)
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
