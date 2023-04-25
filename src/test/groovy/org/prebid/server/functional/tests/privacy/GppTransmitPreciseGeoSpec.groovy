package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.ANALYTICS
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.BIDDER
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.GENERAL_MODULE
import static org.prebid.server.functional.model.request.auction.Condition.ConditionType.RTD_MODULE

class GppTransmitPreciseGeoSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "accounts.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "accounts.%s.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.coun"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = 'requests.activity.processedrules.count'
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"

    def "PBS auction call with bidder allowed in activities should not round lat/lon data and update processed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
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

    def "PBS auction call with bidder rejected in activities should round lat/lon data to 2 digits and update disallowed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
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

    def "PBS auction call when default activity setting set to false should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS auction call when transmit precise geo activities with proper condition type only should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
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

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message == ["Invalid condition type param passed"]

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS auction call when specific bidder in transmit precise geo activities should not round lat/lon data only for required bidder and provide metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            def imp = Imp.defaultImpression.tap {
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.openx = Openx.defaultOpenx
            }
            setAccountId(accountId)
            addImp(imp)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity(activityRules)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and users"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)

        and: "Openx bidder request should contain not rounded geo data for device and user"
        def openxBidderRequest = bidderRequests.find { !it.imp.first().ext.bidder }

        verifyAll {
            openxBidderRequest.device.geo.lat == bidRequest.device.geo.lat
            openxBidderRequest.device.geo.lon == bidRequest.device.geo.lon
            openxBidderRequest.user.geo.lat == bidRequest.user.geo.lat
            openxBidderRequest.user.geo.lon == bidRequest.user.geo.lon
        }

        and: "Generic bidder request should contain rounded geo data for device and user to 2 digits"
        def genericBidderRequest = bidderRequests.find { it.imp.first().ext.bidder }

        verifyAll {
            genericBidderRequest.device.geo.lat.round(2) == genericBidderRequest.device.geo.lat
            genericBidderRequest.device.geo.lon.round(2) == genericBidderRequest.device.geo.lon
            genericBidderRequest.user.geo.lat.round(2) == genericBidderRequest.user.geo.lat
            genericBidderRequest.user.geo.lon.round(2) == genericBidderRequest.user.geo.lon
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

    def "PBS auction call when first rule allowing in activities should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }
    }

    def "PBS auction call when first rule disallowing in activities should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomString
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)


        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call with bidder allowed in activities should not round lat/lon data and update processed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
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

    def "PBS amp call with bidder rejected in activities should round lat/lon data to 2 digits and update disallowed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
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

    def "PBS amp call when default activity setting set to false should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false, rules: [ActivityRule.defaultActivityRule])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call when transmit precise geo activities with proper condition type only should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
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

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message == ["Invalid condition type param passed"]

        where:
        conditions                           | isAllowed
        new Condition(componentType: [])     | true
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | true
        new Condition(componentType: [])     | false
        new Condition(componentType: null)   | false
        new Condition(componentType: [null]) | false
    }

    def "PBS amp call when first rule allowing in activities should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activity rules with same priority"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }
    }

    def "PBS amp call when first rule disallowing in activities should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomString
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Activities set for actions with Generic bidder rejected by hierarchy setup"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidderRequests.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidderRequests.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidderRequests.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }
}
