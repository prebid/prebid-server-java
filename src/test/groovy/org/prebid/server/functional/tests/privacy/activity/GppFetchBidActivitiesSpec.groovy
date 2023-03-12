package org.prebid.server.functional.tests.privacy.activity

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.activitie.Activity
import org.prebid.server.functional.model.request.activitie.ActivityRule
import org.prebid.server.functional.model.request.activitie.AllowActivities
import org.prebid.server.functional.model.request.activitie.Condition
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.BidRequestExt
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT

class GppFetchBidActivitiesSpec extends BaseSpec {

    private static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    private static final boolean CORS_SUPPORT = false
    private static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync2"
    private static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.url"         : USER_SYNC_URL,
            "adapters.${GENERIC.value}.usersync.${USER_SYNC_TYPE.value}.support-cors": CORS_SUPPORT.toString()]
    private static final Map<String, String> RUBICON_CONFIG = [
            "adapters.${RUBICON.value}.enabled"                    : "true",
            "adapters.${RUBICON.value}.usersync.cookie-family-name": RUBICON.value,]
    private static final Map<String, String> APPNEXUS_CONFIG = [
            "adapters.${APPNEXUS.value}.enabled"                    : "true",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name": APPNEXUS.value]
    private static final Map<String, String> PBS_CONFIG = APPNEXUS_CONFIG + RUBICON_CONFIG + GENERIC_CONFIG

    private PrebidServerService prebidServerService = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS should skip call to restricted bidder when restricted in activities component name"() {
        given: "Default bid request with allow activities for fetch bid that restrict bidders in selection"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: generateDefaultFetchBidActivities(conditions, isAllowed)))
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Resolved response should not contain any seatbid"
        assert !bidResponse.seatbid
        and: "Bidder wasn't be call due to restriction of activities"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert !bidderRequests

        where:
        conditions                                                                      | isAllowed
        [new Condition(componentName: Condition.Component.defaultComponent)]            | false
        [new Condition(componentType: new Condition.Component(xIn: ["bidders"]))]       | false
        [new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Condition.Component(notIn: ["bidders"]))]     | true
        [new Condition(componentName: Condition.Component.defaultComponent),
         new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | true
    }

    def "PBS should allow call to bidder when allowed in activities component name"() {
        given: "Default bid request with allow activities for fetch bid that allow bidders in selection"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: generateDefaultFetchBidActivities(conditions, isAllowed)))
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Resolved response should contain seatbid"
        assert bidResponse.seatbid

        and: "Bidder should be called due to positive allow in activities"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests
        where:
        conditions                                                                      | isAllowed
        [new Condition(componentName: Condition.Component.defaultComponent)]            | true
        [new Condition(componentType: new Condition.Component(xIn: ["bidders"]))]       | true
        [new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | false
        [new Condition(componentType: new Condition.Component(notIn: ["bidders"]))]     | false
    }

    def "PBS should allow call to bidder when  activities component with restricted condition have higher priority"() {
        given: "Default bid request with restricted conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = true
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        when: "PBS processes the auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "The resolved response should not contain any seatbid"
        assert !bidResponse.seatbid
        and: "The bidder should not be called due to activities setup"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert !bidderRequests
    }

    def "PBS should allow call to bidder when  activities component with allow condition have higher priority"() {
        given: "Default bid request with allowed conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Condition.Component(xIn: ["bidders"]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        when: "PBS processes the auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Resolved response should contain generic seatbid"
        assert GENERIC == bidResponse.seatbid.first().seat
        and: "The bidder should be called due to activities setup"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert bidderRequests
    }

    def "PBS should allow call to bidder when activities component with conflict condition have same priority"() {
        given: "Default bid request with conditions collision and same priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Condition.Component(xIn: ["bidders"]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Resolved response should NOT contain generic seatbid"
        assert !bidResponse.seatbid.first().seat
        and: "The bidder should NOT be called due to activities setup"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert !bidderRequests
    }

    def "PBS should skip amp call to restricted bidder when restricted in activities component name"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            accountId = PBSUtils.randomString
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: generateDefaultFetchBidActivities(conditions, isAllowed)))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain bidRequest from amp request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests

        where:
        conditions                                                                      | isAllowed
        [new Condition(componentName: Condition.Component.defaultComponent)]            | false
        [new Condition(componentType: new Condition.Component(xIn: ["bidders"]))]       | false
        [new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | true
        [new Condition(componentType: new Condition.Component(notIn: ["bidders"]))]     | true
        [new Condition(componentName: Condition.Component.defaultComponent),
         new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | true
    }

    def "PBS should allow amp call to bidder when allowed in activities component name"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with allow activities settings for fetch bid that allow bidders in selection"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            accountId = PBSUtils.randomString
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: generateDefaultFetchBidActivities(conditions, isAllowed)))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain bidRequest from amp request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site.publisher.id == ampRequest.account as String

        where:
        conditions                                                                      | isAllowed
        [new Condition(componentName: Condition.Component.defaultComponent)]            | true
        [new Condition(componentType: new Condition.Component(xIn: ["bidders"]))]       | true
        [new Condition(componentName: new Condition.Component(notIn: [GENERIC.value]))] | false
        [new Condition(componentType: new Condition.Component(notIn: ["bidders"]))]     | false
    }

    def "PBS should allow amp call to bidder when activities component with restricted condition have higher priority"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with restricted conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = true
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests
    }

    def "PBS should allow amp call to bidder when activities component with allow condition have higher priority"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with allowed conditions and priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Condition.Component(xIn: ["bidders"]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain bidRequest from amp request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site.publisher.id == ampRequest.account as String
    }

    def "PBS should allow amp call to bidder when activities component with conflict condition have same priority"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default bid request with conditions collision and same priority settings"
        def topPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentType: new Condition.Component(xIn: ["bidders"]))
            allow = true
        }

        def defaultPriorityActivity = ActivityRule.defaultActivityRule.tap {
            priority = 1
            condition = new Condition(componentName: new Condition.Component(xIn: [GENERIC.value]))
            allow = false
        }

        AllowActivities fetchActivity = AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = [topPriorityActivity, defaultPriorityActivity]
            }
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext = new BidRequestExt(prebid: new Prebid(allowActivities: fetchActivity))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should not contain bidRequest from amp request"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert !bidderRequests
    }

    private AllowActivities generateDefaultFetchBidActivities(List<Condition> conditions, boolean isAllowed = true) {
        AllowActivities.defaultAllowActivities.tap {
            fetchBid = Activity.defaultActivityRule.tap {
                rules = conditions.collect { singleCondition ->
                    ActivityRule.defaultActivityRule.tap {
                        allow = isAllowed
                        condition = singleCondition
                    }
                }
            }
        }
    }
}
