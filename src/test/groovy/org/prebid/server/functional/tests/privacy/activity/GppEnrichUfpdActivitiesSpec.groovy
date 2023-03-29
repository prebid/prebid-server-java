package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.request.activitie.Activity
import org.prebid.server.functional.model.request.activitie.ActivityRule
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.activitie.Component
import org.prebid.server.functional.model.request.activitie.Condition
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtPrebid
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.USER_ID_MODULE
import static org.prebid.server.functional.model.request.activitie.Condition.ConditionType.RTD_MODULE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP

class GppEnrichUfpdActivitiesSpec extends ActivityBaseSpec {

    static final AllowActivities.ActivityType type = AllowActivities.ActivityType.ENRICH_UFPD

    def "PBS should populate buyeruid from uids cookie when enrich UFDP activities is allowing"() {
        given: "Bid request with buyeruids and allowing enrich UFDP activities"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
            ext.prebid.allowActivities =
                    AllowActivities.getDefaultAllowActivities(type,
                            Activity.getActivityWithRules(conditions, isAllowed))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == uidsCookie.tempUIDs[GENERIC].uid

        where:
        conditions                                                                  | isAllowed
        [new Condition(componentName: Component.defaultComponent)]                  | true
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(xIn: [BIDDER.name]))]                         | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]           | true
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]   | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]         | true
        [new Condition(componentType: new Component(notIn: [RTD_MODULE.name]))]     | true
        [new Condition(componentType: new Component(notIn: [USER_ID_MODULE.name]))] | true
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(notIn: [RTD_MODULE.name]))]                   | true
        [new Condition(componentName: Component.defaultComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))]       | true
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]       | false
        [new Condition(componentType: new Component(xIn: [USER_ID_MODULE.name]))]   | false
    }

    def "PBS should populate buyeruid from uids cookie when enrich UFDP activities is restricting"() {
        given: "Bid request with buyeruids and restricting enrich UFDP activities"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
            ext.prebid.allowActivities =
                    AllowActivities.getDefaultAllowActivities(type,
                            Activity.getActivityWithRules(conditions, isAllowed))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should not populate buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == null

        where:
        conditions                                                                  | isAllowed
        [new Condition(componentName: Component.defaultComponent)]                  | false
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(xIn: [BIDDER.name]))]                         | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]           | false
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]   | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]         | false
        [new Condition(componentType: new Component(notIn: [RTD_MODULE.name]))]     | false
        [new Condition(componentType: new Component(notIn: [USER_ID_MODULE.name]))] | false
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(notIn: [RTD_MODULE.name]))]                   | false
        [new Condition(componentName: Component.defaultComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))]       | false
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]       | true
        [new Condition(componentType: new Component(xIn: [USER_ID_MODULE.name]))]   | true
    }

    def "PBS should not populate buyeruid from uids cookie when activities component has a higher priority restricted condition"() {
        given: "Default bid request with restricted conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with buyeruids and allowing enrich UFDP activities"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain empty buyeruid"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == null
    }

    def "PBS should populate buyeruid from uids cookie when activities component has a higher priority allowed condition"() {
        given: "Allow activities request with priority settings for  enrich ufpd"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with buyeruids and allowing enrich UFDP activities"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == uidsCookie.tempUIDs[GENERIC].uid
    }

    def "PBS should populate buyeruid from uids cookie when activities component has a same priority collision condition"() {
        given: "Allow activities request with priority settings for  enrich ufpd"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with buyeruids and allowing enrich UFDP activities"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User(ext: new UserExt(prebid: new UserExtPrebid(buyeruids: [(GENERIC): ""])))
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        when: "PBS processes auction request"
        prebidServerService.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bidder request should contain buyeruid from the uids cookie"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.buyeruid == uidsCookie.tempUIDs[GENERIC].uid
    }

    def "PBS should set device.lmt = 1 when device.osv for iOS app requests and activity in enrich UFPD is allowed"() {
        given: "Default device with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAtts)
        }
        and: "Bid request with allowing setup for enrich UFPD"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
            ext.prebid.allowActivities =
                    AllowActivities.getDefaultAllowActivities(type,
                            Activity.getActivityWithRules(conditions, isAllowed))
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1

        where:
        conditions                                                                  | isAllowed
        [new Condition(componentName: Component.defaultComponent)]                  | true
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(xIn: [BIDDER.name]))]                         | true
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]           | true
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]   | true
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]         | true
        [new Condition(componentType: new Component(notIn: [RTD_MODULE.name]))]     | true
        [new Condition(componentType: new Component(notIn: [USER_ID_MODULE.name]))] | true
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(notIn: [RTD_MODULE.name]))]                   | true
        [new Condition(componentName: Component.defaultComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))]       | true
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]       | false
        [new Condition(componentType: new Component(xIn: [USER_ID_MODULE.name]))]   | false
    }

    def "PBS should not set device.lmt when device.osv for iOS app requests and activity in enrich UFPD is restricted"() {
        given: "Default device with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAtts)
        }
        and: "Bid request with restricting setup for enrich UFPD"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
            ext.prebid.allowActivities =
                    AllowActivities.getDefaultAllowActivities(type,
                            Activity.getActivityWithRules(conditions, isAllowed))
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should not be set in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == null

        where:
        conditions                                                                  | isAllowed
        [new Condition(componentName: Component.defaultComponent)]                  | false
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(xIn: [BIDDER.name]))]                         | false
        [new Condition(componentType: new Component(xIn: [BIDDER.name]))]           | false
        [new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))]   | false
        [new Condition(componentType: new Component(notIn: [BIDDER.name]))]         | false
        [new Condition(componentType: new Component(notIn: [RTD_MODULE.name]))]     | false
        [new Condition(componentType: new Component(notIn: [USER_ID_MODULE.name]))] | false
        [new Condition(componentName: Component.defaultComponent,
                componentType:
                        new Component(notIn: [RTD_MODULE.name]))]                   | false
        [new Condition(componentName: Component.defaultComponent),
         new Condition(componentName: new Component(notIn: [GENERIC.value]))]       | false
        [new Condition(componentType: new Component(xIn: [RTD_MODULE.name]))]       | true
        [new Condition(componentType: new Component(xIn: [USER_ID_MODULE.name]))]   | true
    }

    def "PBS should not populate device.lmt when device.osv for iOS app requests and activity has a higher priority restricted condition"() {
        given: "Default device with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAtts)
        }

        and: "Activity with restricted conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with allowing setup for enrich UFPD"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should not be set in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == null
    }

    def "PBS should set device.lmt = 1 when device.osv for iOS app requests and activity with higher priority allows enrich UFPD"() {
        given: "Default device with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAtts)
        }

        and: "Activity with restricted conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = false
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with allowing setup for enrich UFPD"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1
    }

    def "PBS should set device.lmt = 1 when device.osv for iOS app requests and activities component has a same priority collision condition"() {
        given: "Default device with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
            it.osv = "14.0"
            it.ext = new DeviceExt(atts: randomAtts)
        }

        and: "Allow activities request with priority settings for  enrich ufpd"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Component(xIn: [GENERAL_MODULE.name]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities enrichUfpdActivity =
                AllowActivities.getDefaultAllowActivities(type,
                        Activity.getDefaultActivity([topPriorityActivity, defaultPriorityActivity]))

        and: "Bid request with allowing setup for enrich UFPD"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: enrichUfpdActivity))
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1
    }

    private static getRandomAtts() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

}
