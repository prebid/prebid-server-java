package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Component
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.ActivityType
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

// TODO due to deprecated status rework into transmitUfpd tests
class GppTransmitUfpdActivitiesSpec extends ActivityBaseSpec {

    final ActivityType type = ActivityType.TRANSMIT_UFPD

    def "PBS should process user.eids in active request when allow activities settings allow for bidder"() {
        given: "Allow activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def bidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        when: "PBS processes auction requests"
        pbsServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should include user.eids in request"
        def generalBidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert generalBidderRequest.user.eids

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | true
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | false
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS should remove user.eids before sending to component in active request when allow activities settings decline for bidder"() {
        given: "Reject activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def bidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        when: "PBS processes auction requests"
        pbsServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should remove user.eids in request"
        def generalBidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !generalBidderRequest.user.eids

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | false
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS should remove user.eids before sending to component in active request when allow activities settings decline for requested module"() {
        given: "Reject activities setup"
        Condition condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
        Activity activity = Activity.getActivityWithRules([condition], false)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def generalBidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Default Openx BidRequest with user.eids and account id"
        def openxBidRequest = getBidRequestWithAccount(SITE, accountId, OPENX)

        when: "PBS processes auction requests"
        pbsServerService.sendAuctionRequest(generalBidRequest)
        pbsServerService.sendAuctionRequest(openxBidRequest)

        then: "General bidder request should remove user.eids from request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !generalBidderRequest.user.eids

        then: "Openx bidder request should remove user.eids from request"
        def openxBidderRequest = bidder.getBidderRequest(openxBidRequest.id)
        assert !openxBidderRequest.user.eids
    }

    def "PBS should remove user.eids in active request when activities component with restricted condition have higher priority"() {
        given: "Activity rules with different priority"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = true
        }
        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def generalBidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        when: "PBS processes auction request"
        pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "General bidder request should remove user.eids from request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert !generalBidderRequest.user.eids
    }

    def "PBS should process user.eids in active request when activities component with allow condition have higher priority"() {
        given: "Activity rules with different priority"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERIC.value]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def generalBidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        when: "PBS processes auction request"
        pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "General bidder request should remove user.eids from request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest.user.eids
    }

    def "PBS should process user.eids in active request when activities component with conflict condition have same priority"() {
        given: "Activity rules with different priority"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [BIDDER.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Save account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Default Generic BidRequest with user.eids and account id"
        def generalBidRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        when: "PBS processes auction request"
        pbsServerService.sendAuctionRequest(generalBidRequest)

        then: "General bidder request should leave user.eids from request"
        def generalBidderRequest = bidder.getBidderRequest(generalBidRequest.id)
        assert generalBidderRequest.user.eids
    }

    def "PBS should process user.eids in AMP request when allow activities settings allowing for bidder"() {
        given: "Allow activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
        }

        and: "Default Generic BidRequest with user.eids and account id"
        def ampStoredRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should include user.eids in request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest

        assert resolvedRequest.user.eids

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | true
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | true
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | true
        [new Condition(componentType: new Component(notIn: [OPENX.value]))]   | false
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS should remove user.eids in AMP request when allow activities settings allowing for bidder"() {
        given: "Reject activities setup"
        Activity activity = Activity.getActivityWithRules(conditions, isAllowed)
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
        }

        and: "Default Generic BidRequest with user.eids and account id"
        def ampStoredRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should include user.eids in request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest

        assert !resolvedRequest.user.eids

        where:
        conditions                                                            | isAllowed
        [new Condition(componentName: Component.baseComponent)]               | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(xIn: [BIDDER.name]))]            | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]     | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]   | false
        [new Condition(componentName: Component.baseComponent,
                componentType: new Component(notIn: [RTD_MODULE.name]))]      | false
        [new Condition(componentName: Component.baseComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Component(xIn: [OPENX.value]))]     | false
    }

    def "PBS should remove user.eids in AMP request when activities component with restricted condition have higher priority"() {
        given: "Allow activity rules with different priority"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = true
        }
        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
        }

        and: "Default Generic BidRequest with user.eids and account id"
        def ampStoredRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should include user.eids in request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest

        assert !resolvedRequest.user.eids
    }

    def "PBS should process user.eids in AMP request when activities component with allow condition have higher priority"() {
        given: "Default bid request with allowed conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERIC.value]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
        }

        and: "Default Generic BidRequest with user.eids and account id"
        def ampStoredRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should include user.eids in request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest

        assert resolvedRequest.user.eids
    }

    def "PBS should process user.eids in AMP request when activities component with conflict condition have same priority"() {
        given: "Default bid request with conditions collision and same priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [BIDDER.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        Activity activity = Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity])
        AllowActivities allowSetup = AllowActivities.getDefaultAllowActivities(type, activity)

        and: "Saved account config with allow activities into DB"
        def accountId = PBSUtils.randomNumber as String
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            tagId = PBSUtils.randomString
            account = accountId
        }

        and: "Default Generic BidRequest with user.eids and account id"
        def ampStoredRequest = getBidRequestWithAccount(accountId).tap {
            user.eids = [Eid.defaultEid]
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def ampResponse = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should include user.eids in request"
        def resolvedRequest = ampResponse.ext.debug.resolvedRequest

        assert resolvedRequest.user.eids
    }
}
