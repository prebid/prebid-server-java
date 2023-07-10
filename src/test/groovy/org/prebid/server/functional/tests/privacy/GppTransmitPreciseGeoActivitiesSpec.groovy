package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGppConfig
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.PrivacyModule.*
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO

class GppTransmitPreciseGeoActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String ALERT_GENERAL = "alert.general"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"

    def "PBS auction call with bidder allowed in activities should not round lat/lon data and update processed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.defaultActivity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction call with bidder rejected in activities should round lat/lon data to 2 digits and update disallowed metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction call when default activity setting set to false should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = new Activity(defaultAction: false)
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS auction call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(conditions, isAllowed)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS auction call when first rule allowing in activities should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activity rules"
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)

        and: "Activities set for bidder allowed by hierarchy structure"
        def activity = Activity.getDefaultActivity([allowActivity, disallowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }
    }

    def "PBS auction call when first rule disallowing in activities should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activity rules"
        def disallowActivity = new ActivityRule(condition: Condition.baseCondition, allow: false)
        def allowActivity = new ActivityRule(condition: Condition.baseCondition, allow: true)

        and: "Activities set for bidder disallowing by hierarchy structure"
        def activity = Activity.getDefaultActivity([disallowActivity, allowActivity])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS auction should allow rule when gppSid not intersect"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            regs.gppSid = regsGppSid
            ext.prebid.trace = VERBOSE
            setAccountId(accountId)
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = conditionGppSid
        }

        and: "Setup activities"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
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
        regsGppSid        | conditionGppSid
        null              | [USP_V1.intValue]
        [USP_V1.intValue] | null
    }

    def "PBS auction shouldn't allow rule when gppSid intersect"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            regs.gppSid = [USP_V1.intValue]
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = [USP_V1.intValue]
        }

        and: "Activities set with bidder allowed"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction should process rule when device.geo doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.regs.gppSid = [USP_V1.intValue]
            it.ext.prebid.trace = VERBOSE
            it.device.geo = deviceGeo.tap {
                lat = PBSUtils.getRandomDecimal(0, 90)
                lon = PBSUtils.getRandomDecimal(0, 90)
            }
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = [USP_V1.intValue]
            it.geo = conditionGeo
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
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
        deviceGeo                                           | conditionGeo
        new Geo(country: USA,) | null
        new Geo(region: ALABAMA.abbreviation)               | [USA.withState(ALABAMA)]
        new Geo(country: CAN, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
    }

    def "PBS auction should process rule when device.geo is null and doesn't intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.regs.gppSid = [USP_V1.intValue]
            it.ext.prebid.trace = VERBOSE
            it.device.geo = null
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = [PBSUtils.randomString]
            it.gppSid = [USP_V1.intValue]
            it.geo = ["$USA.value".toString()]
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
            !bidderRequests.device.geo
            !bidderRequests.device.geo
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should disallowed rule when device.geo intersection"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.regs.gppSid = null
            it.ext.prebid.trace = VERBOSE
            it.device.geo = deviceGeo.tap {
                lat = PBSUtils.getRandomDecimal(0, 90)
                lon = PBSUtils.getRandomDecimal(0, 90)
            }
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = null
            it.geo = conditionGeo
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.value]
        new Geo(country: USA)                               | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
    }

    def "PBS auction call when privacy regulation match and disabled should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS auction call when privacy regulation restring but sid excluded should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1])

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }
    }

    def "PBS auction call when privacy regulation not exist for account should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with non-existed privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Existed account with empty privacy regulations settings"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }
    }

    def "PBS auction call when privacy regulation have duplicate should include first, not round lat/lon data and populate metric"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        def response = activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } ==
                ["Invalid allowActivities config for account: ${accountId}"] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }
    }

    def "PBS auction call when privacy regulation match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)
        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS auction call when privacy regulation match and allowing by first element in hierarchy should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmit precise geo with rejecting privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS auction call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmit precise geo with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with multiple array").size() == 1
    }

    def "PBS amp call with bidder allowed in activities should not round lat/lon data and update processed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp call with bidder rejected in activities should round lat/lon data to 2 digits and update disallowed metrics"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, false)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp call when default activity setting set to false should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def activity = new Activity(defaultAction: false)
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call when bidder allowed activities have invalid condition type should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} " +
                "contains conditional rule with empty array").size() == 1

        where:
        conditions                       | isAllowed
        new Condition(componentType: []) | true
        new Condition(componentType: []) | false
        new Condition(componentName: []) | true
        new Condition(componentName: []) | false
    }

    def "PBS amp call when first rule allowing in activities should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }
    }

    def "PBS amp call when first rule disallowing in activities should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call when privacy regulation match and disabled should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }

        where:
        privacyAllowRegulations << [IAB_US_GENERIC, IAB_ALL, ALL]
    }

    def "PBS amp call when privacy regulation restring but sid excluded should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmit precise geo with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [USP_NAT_V1])

        and: "Existed account with cookie sync and allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }
    }

    def "PBS amp call when privacy regulation not exist for account should not round lat/lon data"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with rejecting privacy regulation and sid exception"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Existed account with allow activities setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
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
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | null
        new Geo(region: ALABAMA.abbreviation)               | [USA.withState(ALABAMA)]
        new Geo(country: CAN, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
    }

    def "PBS amp call when privacy regulation have duplicate should include first, not round lat/lon data and populate metric"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppUsNatRejectConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain proper warning"
        assert response.ext.warnings[ErrorType.PREBID].collect { it.message } ==
                ["Invalid allowActivities config for account: ${accountId}"] // TODO replace with actual error message

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }
    }

    def "PBS amp call when privacy regulation match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call when privacy regulation match and allowing by first element in hierarchy should round lat/lon data to 2 digits"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with multiple privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC]
        }

        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric, ruleIabAll]))

        and: "Multiple account gpp privacy regulation config"
        def accountGppUsNatConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_US_GENERIC, [], false)
        def accountGppTfcEuConfig = AccountGppConfig.getDefaultAccountGppConfig(IAB_TFC_EU)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatConfig, accountGppTfcEuConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }
    }

    def "PBS amp call when privacy regulation rule have multiple modules should skip this rule and emit an error"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Generic BidRequests with TID fields and account id"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "Activities set for transmit precise geo with invalid privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERIC, IAB_TFC_EU]
        }

        where:
        deviceGeo                                           | conditionGeo
        new Geo(country: USA)                               | [USA.value]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
        accountDao.save(account)

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def logs = activityPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Activity configuration for account ${accountId} contains conditional rule with multiple array").size() == 1
    }

    def "PBS auction should process rule when regs.ext.gpc doesn't intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should disallowed rule when regs.ext.gpc intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.regs.gppSid = null
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = "1"
        }

        and: "Setup activity"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gppSid = null
            it.gpc = "1"
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS auction should process rule when header gpc doesn't intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = PBSUtils.randomNumber as String
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": "1"])

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == bidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == bidRequest.device.geo.lat
            bidderRequests.device.geo.lon == bidRequest.device.geo.lon
            bidderRequests.user.geo.lat == bidRequest.user.geo.lat
            bidderRequests.user.geo.lon == bidRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
        assert metrics[ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT.formatted(accountId)] == 1
    }

    def "PBS auction should process rule when header gpc intersection with condition.gpc"() {
        given: "Generic bid request with account connection"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            it.ext.prebid.trace = VERBOSE
            it.regs.ext.gpc = null
        }

        and: "Setup condition"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = "1"
        }

        and: "Set activity"
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Set up account for allow activities"
        def account = getAccountWithAllowActivities(accountId, activities)
        accountDao.save(account)

        when: "PBS processes auction request"
        activityPbsService.sendAuctionRequest(bidRequest, ["Sec-GPC": "1"])

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            bidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            bidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            bidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            bidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_ACCOUNT.formatted(accountId)] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }

    def "PBS amp should process rule when header gpc doesn't intersection with condition.gpc"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = PBSUtils.randomNumber as String
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": VALID_VALUE_FOR_GPC_HEADER])

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == ampStoredRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequests.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequests.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequests.user.geo.lon == ampStoredRequest.user.geo.lon
        }

        and: "Metrics processed across activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ACTIVITY_RULES_PROCESSED_COUNT] == 1
    }

    def "PBS amp should disallow rule when header gpc intersection with condition.gpc"() {
        given: "Default bid request with allow activities settings for fetch bid that decline bidders in selection"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
        }

        and: "Allow activities setup"
        def gpc = "1"
        def condition = Condition.baseCondition.tap {
            it.componentType = null
            it.componentName = null
            it.gpc = gpc
        }
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(condition, false)])
        def allowSetup = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, activity)

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Saved account config with allow activities into DB"
        def account = getAccountWithAllowActivities(accountId, allowSetup)
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request with header"
        activityPbsService.sendAmpRequest(ampRequest, ["Sec-GPC": gpc])

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            ampStoredRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            ampStoredRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            ampStoredRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            ampStoredRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[DISALLOWED_COUNT_FOR_ACTIVITY_RULE] == 1
        assert metrics[DISALLOWED_COUNT_FOR_GENERIC_ADAPTER] == 1
    }
}
