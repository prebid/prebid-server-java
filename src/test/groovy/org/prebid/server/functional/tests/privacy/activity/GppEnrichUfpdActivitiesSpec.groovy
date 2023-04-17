package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Component
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner
import org.prebid.server.functional.testcontainers.scaffolding.pg.UserData
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.INVALID
import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.HIGHEST
import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class GppEnrichUfpdActivitiesSpec extends ActivityBaseSpec {
    private static final GeneralPlanner generalPlanner = new GeneralPlanner(Dependencies.networkServiceContainer)
    private static final UserData userData = new UserData(Dependencies.networkServiceContainer)
    private static final ActivityType type = ENRICH_UFPD
    private static final Device testDevice = new Device().tap {
        it.os = PBSUtils.randomizeCase("iOS")
        it.osv = "14.0"
        it.ext = new DeviceExt(atts: randomAttribute)
    }

    def "PBS activities call when enrich UFDP activities is allowing should enhance user.data"() {
        given: "Activities set with bidder allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | true
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

    def "PBS activities call when enrich UFDP activities is rejecting should not enhance user.data"() {
        given: "Activities set with bidder allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data == generalBidRequest?.user?.data

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | false
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

    def "PBS activities call when activities settings set to empty should enhance user.data"() {
        given: "Empty activities setup"
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data

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

    def "PBS activities call when specific allow hierarchy in enrich UFDP activities should enhance user.data"() {
        given: "Activities set with with generic bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data

        where:
        rules << [
                [new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)]
        ]
    }

    def "PBS activities call when specific reject hierarchy in enrich UFDP activities should not enhance user.data"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Processed bidder request should contain exactly the same user.data"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data == generalBidRequest?.user?.data
    }

    def "PBS activities call when invalid hierarchy in enrich UFDP activities should enhance user.data"() {
        given: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "User Service Response is set to return default response"
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(accountId)

        and: "Planner Mock line items"
        def plansGeneralResponse = PlansResponse.getDefaultPlansResponse(generalBidRequest.site.publisher.id)
        generalPlanner.initPlansResponse(plansGeneralResponse)

        and: "Line items are fetched by PBS"
        updateLineItemsAndWait()

        and: "Cookies with user ids"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "Sending auction request to PBS"
        pbsServerService.sendAuctionRequest(generalBidRequest, cookieHeader)

        then: "Bidder request should contain additional user.data from processed request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest?.user?.data
        assert !generalBidRequest?.user?.data
    }

    def "PBS activities call when enrich UFDP activities is allowing should enhance request.device"() {
        given: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(APP, accountId, OPENX).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        then: "Openx bidder should be called due to positive allow in activities"
        def openxBidderRequest = bidder.getBidderRequest(openxBidRequest.id)
        assert openxBidderRequest.device.lmt == 1

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

    def "PBS activities call when enrich UFDP activities is restricting should not enhance request.device"() {
        given: "Activities set with all bidders allowed"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(APP, accountId, OPENX).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt

        then: "Openx bidder should be called due to positive allow in activities"
        def openxBidderRequest = bidder.getBidderRequest(openxBidRequest.id)
        assert !openxBidderRequest.device.lmt

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

    def "PBS activities call when specific bidder in enrich UFDP activities should enhance request.device only bidder"() {
        given: "Reject activities setup"
        def activity = Activity.getActivityWithRules(conditions, isAllowed)
        def allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        and: "Openx bid request with account connection"
        def openxBidRequest = getBidRequestWithAccount(APP, accountId, OPENX).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)
        def openxResponse = pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Resolved response should contain seatbid for Openx request"
        assert openxResponse.seatbid.first().seat == OPENX

        and: "Generic bidder should enhance device in request"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        then: "Openx bidder should not enhance device in request"
        def openxBidderRequest = bidder.getBidderRequest(openxBidRequest.id)
        assert openxBidderRequest.device.lmt == 1

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | false
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

    def "PBS activities call when activities settings set to empty should enhance request.device"() {
        given: "Empty activities setup"
        def allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

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

    def "PBS activities call when specific allow hierarchy in enrich UFDP activities should enhance request.device"() {
        given: "Activities set with with generic bidders allowed by hierarchy config"
        def activity = Activity.getDefaultActivity(rules)
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1

        where:
        rules << [
                [new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)],
                [new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true),
                 new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: false)]
        ]
    }

    def "PBS activities call when specific reject hierarchy in enrich UFDP activities should not enhance request.device"() {
        given: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def topPriorityActivity = new ActivityRule(priority: HIGHEST, condition: Condition.baseCondition, allow: false)
        def defaultPriorityActivity = new ActivityRule(priority: DEFAULT, condition: Condition.baseCondition, allow: true)
        def activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        def activities = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !genericBidderRequest.device.lmt
    }

    def "PBS activities call when invalid hierarchy in enrich UFDP activities should enhance request.device"() {
        given: "Activities set for activities with invalid priority setup"
        def invalidRule = new ActivityRule(priority: INVALID, condition: Condition.baseCondition, allow: false)
        def invalidActivity = Activity.getDefaultActivity([invalidRule])
        def activities = AllowActivities.getDefaultAllowActivities(type, invalidActivity)

        and: "Existed account with allow activities setup"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Default device with device.os = iOS and any device.ext.atts"

        and: "Generic bid request with account connection"
        def generalBidRequest = getBidRequestWithAccount(APP, accountId, GENERIC).tap {
            it.device = testDevice
        }

        when: "PBS processes auction requests"
        def genericResponse = pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "Resolved response should contain seatbid for Generic request"
        assert genericResponse.seatbid.first().seat == GENERIC

        and: "Generic bidder should be called due to positive allow in activities"
        def genericBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert genericBidderRequest.device.lmt == 1
    }

    private static getRandomAttribute() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

    protected void updateLineItemsAndWait() {
        def initialPlansRequestCount = generalPlanner.recordedPlansRequestCount
        pbsServerService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == initialPlansRequestCount + 1 }
    }
}
