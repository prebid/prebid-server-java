package org.prebid.server.functional.tests.privacy


import org.prebid.server.functional.model.config.privacy.AccountGppConfig
import org.prebid.server.functional.model.config.privacy.ActivityConfig
import org.prebid.server.functional.model.config.privacy.EqualityValueRule
import org.prebid.server.functional.model.config.privacy.GppModuleConfig
import org.prebid.server.functional.model.config.privacy.InequalityValueRule
import org.prebid.server.functional.model.config.privacy.LogicalRestrictedRule
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.gpp.UspCaV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspCoV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspCtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspNatV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspUtV1Consent
import org.prebid.server.functional.util.privacy.gpp.UspVaV1Consent
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData

import java.time.Instant

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.privacy.DataActivity.CONSENT
import static org.prebid.server.functional.model.config.privacy.DataActivity.NOTICE_NOT_PROVIDED
import static org.prebid.server.functional.model.config.privacy.DataActivity.NOTICE_PROVIDED
import static org.prebid.server.functional.model.config.privacy.DataActivity.NOT_APPLICABLE
import static org.prebid.server.functional.model.config.privacy.DataActivity.NO_CONSENT
import static org.prebid.server.functional.model.config.privacy.LogicalOperation.AND
import static org.prebid.server.functional.model.config.privacy.LogicalOperation.OR
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.CHILD_CONSENTS_BELOW_13
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.CHILD_CONSENTS_FROM_13_TO_16
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.GPC
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_ACCOUNT_INFO
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_BIOMETRIC_ID
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_CITIZENSHIP_STATUS
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_COMMUNICATION_CONTENTS
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_GENETIC_ID
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_GEOLOCATION
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_HEALTH_INFO
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_ID_NUMBERS
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_ORIENTATION
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SENSITIVE_DATA_RELIGIOUS_BELIEFS
import static org.prebid.server.functional.model.config.privacy.UsNationalPrivacySection.SHARING_NOTICE
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.request.GppSectionId.USP_CA_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CO_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_CT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_NAT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_UT_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_V1
import static org.prebid.server.functional.model.request.GppSectionId.USP_VA_V1
import static org.prebid.server.functional.model.request.amp.ConsentType.GPP
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.PrivacyModule.ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_ALL
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_TFC_EU
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_CUSTOM_LOGIC
import static org.prebid.server.functional.model.request.auction.PrivacyModule.IAB_US_GENERAL
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.model.State.ALABAMA
import static org.prebid.server.functional.util.privacy.model.State.ONTARIO

class GppTransmitPreciseGeoActivitiesSpec extends PrivacyBaseSpec {

    private static final String ACTIVITY_PROCESSED_RULES_FOR_ACCOUNT = "account.%s.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACCOUNT = "account.%s.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String ACTIVITY_RULES_PROCESSED_COUNT = "requests.activity.processedrules.count"
    private static final String DISALLOWED_COUNT_FOR_ACTIVITY_RULE = "requests.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String DISALLOWED_COUNT_FOR_GENERIC_ADAPTER = "adapter.${GENERIC.value}.activity.${TRANSMIT_PRECISE_GEO.metricValue}.disallowed.count"
    private static final String ALERT_GENERAL = "alerts.general"

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
        new Geo(country: USA,)                              | null
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
        new Geo(country: USA, region: ALABAMA.abbreviation) | [USA.withState(ALABAMA)]
        new Geo(country: USA, region: ALABAMA.abbreviation) | [CAN.withState(ONTARIO), USA.withState(ALABAMA)]
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities)
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

    def "PBS auction call when privacy regulation match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitPreciseGeINTERNAL_SERVER_ERRORo with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS auction call when privacy module contain some part of disallow logic should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = disallowGppLogic
        }

        and: "Activities set for transmitPreciseGeo with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build(),
                new UspNatV1Consent.Builder().setSensitiveDataProcessingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSensitiveDataLimitUseNotice(2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UspNatV1Consent.Builder().setPersonalDataConsents(2).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 2
                        )).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 2
                        )).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 1
                        )).build()
        ]
    }

    def "PBS auction call when request have different gpp consent but match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequests with gppConsent and account id"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppConsent
        }

        and: "Activities set for transmitPreciseGeo with rejecting privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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

        where:
        gppConsent                                                          | gppSid
        new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build() | USP_NAT_V1
        new UspCaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CA_V1
        new UspVaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_VA_V1
        new UspCoV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CO_V1
        new UspUtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_UT_V1
        new UspCtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CT_V1
    }

    def "PBS auction call when privacy modules contain allowing settings should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

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
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: true)
        ]
    }

    def "PBS auction call when regs.gpp in request is allowing should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = regsGpp
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

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
        regsGpp << ["", new UspNatV1Consent.Builder().build(), new UspNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS auction call when privacy regulation have duplicate should process request and update alerts metrics"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
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

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when privacy module contain invalid code should respond with an error"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def bidRequest = bidRequestWithGeo.tap {
            it.setAccountId(accountId)
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = SIMPLE_GPC_DISALLOW_LOGIC
        }

        and: "Activities set for transmitPreciseGeo with rejecting privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleIabAll]))

        and: "Invalid account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == UNAUTHORIZED.code()
        assert error.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS auction call when privacy regulation don't match custom requirement should not round lat/lon data"() {
        given: "Default basic generic BidRequest"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()
        def accountId = PBSUtils.randomNumber as String
        def genericBidRequest = bidRequestWithGeo.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set for transmit precise geo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(genericBidRequest)

        then: "Bidder request should contain not rounded geo data for device and user"
        def bidderRequests = bidder.getBidderRequest(genericBidRequest.id)

        verifyAll {
            bidderRequests.device.ip == genericBidRequest.device.ip
            bidderRequests.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequests.device.geo.lat == genericBidRequest.device.geo.lat
            bidderRequests.device.geo.lon == genericBidRequest.device.geo.lon
            bidderRequests.user.geo.lat == genericBidRequest.user.geo.lat
            bidderRequests.user.geo.lon == genericBidRequest.user.geo.lon
        }

        where:
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS auction call when privacy regulation match custom requirement should round lat/lon data to 2 digits"() {
        given: "Default basic generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = bidRequestWithGeo.tap {
            regs.gppSid = [USP_NAT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set for transmit precise geo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(generalBidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            generalBidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            generalBidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            generalBidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            generalBidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        where:
        gppConsent                                                | valueRules
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(false).build()       | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
    }

    def "PBS auction call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Generic BidRequest with gpp and account setup"
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = bidRequestWithGeo.tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [USP_CT_V1.intValue]
            regs.gpp = gppConsent
            setAccountId(accountId)
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], restrictedRule), [USP_CT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "JsonLogic exception: objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS auction call when custom privacy regulation with normalizing should change request consent and call to bidder"() {
        given: "Generic BidRequest with gpp and account setup"
        def accountId = PBSUtils.randomNumber as String
        def generalBidRequest = bidRequestWithGeo.tap {
            ext.prebid.trace = VERBOSE
            regs.gppSid = [gppSid.intValue]
            regs.gpp = gppStateConsent.build()
            setAccountId(accountId)
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([TRANSMIT_PRECISE_GEO], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with enabled normalizeFlag"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(generalBidRequest)

        then: "Bidder request should contain rounded geo data for device and user to 2 digits"
        def bidderRequests = bidder.getBidderRequest(generalBidRequest.id)

        verifyAll {
            bidderRequests.device.ip == "43.77.114.0"
            bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
            generalBidRequest.device.geo.lat.round(2) == bidderRequests.device.geo.lat
            generalBidRequest.device.geo.lon.round(2) == bidderRequests.device.geo.lon
            generalBidRequest.user.geo.lat.round(2) == bidderRequests.user.geo.lat
            generalBidRequest.user.geo.lon.round(2) == bidderRequests.user.geo.lon
        }

        where:
        gppSid    | equalityValueRules                                                      | gppStateConsent
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), 1, PBSUtils.getRandomNumber(0, 2))
    }

    def "PBS amp call with bidder allowed in activities should not round lat/lon data and update processed metrics"() {
        given: "Default bid request with allow activities settings that decline bidders in selection"
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
        given: "Default bid request with allow activities settings that decline bidders in selection"
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
        given: "Default bid request with allow activities settings that decline bidders in selection"
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

        and: "Default bid request with allow activities settings that decline bidders in selection"
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
        given: "Default bid request with allow activities settings that decline bidders in selection"
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
        given: "Default bid request with allow activities settings that decline bidders in selection"
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

    def "PBS amp should process rule when header gpc doesn't intersection with condition.gpc"() {
        given: "Default bid request with allow activities settings that decline bidders in selection"
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
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
        given: "Default bid request with allow activities settings"
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
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, allowSetup)
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

    def "PBS amp call when privacy regulation match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [privacyAllowRegulations]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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

        where:
        privacyAllowRegulations << [IAB_US_GENERAL, IAB_ALL, ALL]
    }

    def "PBS amp call when privacy module contain some part of disallow logic should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = disallowGppLogic
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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

        where:
        disallowGppLogic << [
                SIMPLE_GPC_DISALLOW_LOGIC,
                new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build(),
                new UspNatV1Consent.Builder().setSensitiveDataProcessingOptOutNotice(2).build(),
                new UspNatV1Consent.Builder().setSensitiveDataLimitUseNotice(2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 1).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2).build(),
                new UspNatV1Consent.Builder().setKnownChildSensitiveDataConsents(1, 0).build(),
                new UspNatV1Consent.Builder().setPersonalDataConsents(2).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataLimitUseNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 2
                        )).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 2
                        )).build(),
                new UspNatV1Consent.Builder()
                        .setSensitiveDataProcessingOptOutNotice(0)
                        .setSensitiveDataProcessing(new UsNationalSensitiveData(
                                geolocation: 1
                        )).build()
        ]
    }

    def "PBS amp call when request have different gpp consent but match and rejecting should round lat/lon data to 2 digits"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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

        where:
        gppConsent                                                          | gppSid
        new UspNatV1Consent.Builder().setMspaServiceProviderMode(1).build() | USP_NAT_V1
        new UspCaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CA_V1
        new UspVaV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_VA_V1
        new UspCoV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CO_V1
        new UspUtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_UT_V1
        new UspCtV1Consent.Builder().setMspaServiceProviderMode(1).build()  | USP_CT_V1
    }

    def "PBS amp call when privacy modules contain allowing settings should not round lat/lon data"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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
        accountGppConfig << [
                new AccountGppConfig(code: IAB_US_GENERAL, enabled: false),
                new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: true)
        ]
    }

    def "PBS amp call when regs.gpp in request is allowing should not round lat/lon data"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = regsGpp
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration"
        def accountGppConfig = new AccountGppConfig(code: IAB_US_GENERAL, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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
        regsGpp << ["", new UspNatV1Consent.Builder().build(), new UspNatV1Consent.Builder().setGpc(false).build()]
    }

    def "PBS amp call when privacy regulation have duplicate should process request and update alerts metrics"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_GENERAL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Account gpp privacy regulation configs with conflict"
        def accountGppUsNatAllowConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: [USP_NAT_V1]), enabled: false)
        def accountGppUsNatRejectConfig = new AccountGppConfig(code: IAB_US_GENERAL, config: new GppModuleConfig(skipSids: []), enabled: true)

        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppUsNatAllowConfig, accountGppUsNatRejectConfig])
        accountDao.save(account)

        and: "Stored request in DB"
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

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when privacy module contain invalid code should respond with an error"() {
        given: "Default Generic BidRequest"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo

        and: "amp request with link to account"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = SIMPLE_GPC_DISALLOW_LOGIC
            it.consentType = GPP
        }

        and: "Activities set for transmitPreciseGeo with privacy regulation"
        def ruleIabAll = new ActivityRule().tap {
            it.privacyRegulation = [IAB_ALL]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleIabAll]))

        and: "Invalid account gpp privacy regulation config"
        def accountGppTfcEuConfig = new AccountGppConfig(code: IAB_TFC_EU, enabled: true)

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppTfcEuConfig])
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == UNAUTHORIZED.code()
        assert error.responseBody == "Unauthorized account id: ${accountId}"
    }

    def "PBS amp call when privacy regulation don't match custom requirement should not round lat/lon data in request"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UspNatV1Consent.Builder().setGpc(gpcValue).build()
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmit precise geo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], accountLogic))
        }

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
        gpcValue | accountLogic
        false    | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new EqualityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(OR, [new InequalityValueRule(GPC, NOTICE_PROVIDED)])
        true     | LogicalRestrictedRule.generateSingleRestrictedRule(AND, [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                            new EqualityValueRule(SHARING_NOTICE, NOTICE_PROVIDED)])
    }

    def "PBS amp call when privacy regulation match custom requirement should round lat/lon data to 2 digits"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.value
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set for transmit precise geo with allowing privacy regulation"
        def rule = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }

        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([rule]))

        and: "Account gpp configuration with sid skip"
        def accountLogic = LogicalRestrictedRule.generateSingleRestrictedRule(OR, valueRules)
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], accountLogic))
        }

        and: "Existed account with privacy regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
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

        where:
        gppConsent                                                | valueRules
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(false).build()       | [new InequalityValueRule(GPC, NOTICE_PROVIDED)]
        new UspNatV1Consent.Builder().setGpc(true).build()        | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
        new UspNatV1Consent.Builder().setSharingNotice(2).build() | [new EqualityValueRule(GPC, NOTICE_PROVIDED),
                                                                     new EqualityValueRule(SHARING_NOTICE, NOTICE_NOT_PROVIDED)]
    }

    def "PBS amp call when custom privacy regulation empty and normalize is disabled should respond with an error and update metric"() {
        given: "Store bid request with gpp string and link for account"
        def accountId = PBSUtils.randomNumber as String
        def gppConsent = new UspNatV1Consent.Builder().setGpc(true).build()
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gppSid"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = USP_NAT_V1.intValue
            it.consentString = gppConsent
            it.consentType = GPP
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Account gpp configuration with empty Custom logic"
        def restrictedRule = LogicalRestrictedRule.rootLogicalRestricted
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(new ActivityConfig([TRANSMIT_PRECISE_GEO], restrictedRule), [USP_NAT_V1], false)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp requests"
        activityPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        def error = thrown(PrebidServerException)
        assert error.statusCode == BAD_REQUEST.code()
        assert error.responseBody == "Invalid account configuration: JsonLogic exception: " +
                "objects must have exactly 1 key defined, found 0"

        and: "Metrics for disallowed activities should be updated"
        def metrics = activityPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1
    }

    def "PBS amp call when custom privacy regulation with normalizing should change request consent and call to bidder"() {
        given: "Store bid request with link for account"
        def accountId = PBSUtils.randomNumber as String
        def ampStoredRequest = bidRequestWithGeo.tap {
            setAccountId(accountId)
        }

        and: "amp request with link to account and gpp"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.account = accountId
            it.gppSid = gppSid.intValue
            it.consentString = gppStateConsent.build()
            it.consentType = GPP
        }

        and: "Activities set with privacy regulation"
        def ruleUsGeneric = new ActivityRule().tap {
            it.privacyRegulation = [IAB_US_CUSTOM_LOGIC]
        }
        def activities = AllowActivities.getDefaultAllowActivities(TRANSMIT_PRECISE_GEO, Activity.getDefaultActivity([ruleUsGeneric]))

        and: "Activity config"
        def activityConfig = new ActivityConfig([TRANSMIT_PRECISE_GEO], LogicalRestrictedRule.generateSingleRestrictedRule(AND, equalityValueRules))

        and: "Account gpp configuration with enabled normalizeFlag"
        def accountGppConfig = new AccountGppConfig().tap {
            it.code = IAB_US_CUSTOM_LOGIC
            it.enabled = true
            it.config = GppModuleConfig.getDefaultModuleConfig(activityConfig, [gppSid], true)
        }

        and: "Flush metrics"
        flushMetrics(activityPbsService)

        and: "Existed account with gpp regulation setup"
        def account = getAccountWithAllowActivitiesAndPrivacyModule(accountId, activities, [accountGppConfig])
        accountDao.save(account)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp requests"
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

        where:
        gppSid    | equalityValueRules                                                      | gppStateConsent
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ID_NUMBERS, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(idNumbers: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ACCOUNT_INFO, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(accountInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geolocation: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(racialEthnicOrigin: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_COMMUNICATION_CONTENTS, CONSENT)] | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(communicationContents: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(geneticId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(biometricId: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(healthInfo: 2))
        USP_CA_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspCaV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsCaliforniaSensitiveData(orientation: 2))
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(0, 0)
        USP_CA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2), PBSUtils.getRandomNumber(1, 2))

        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspVaV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_VA_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspVaV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCoV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_CO_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCoV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RACIAL_ETHNIC_ORIGIN, CONSENT)]   | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(racialEthnicOrigin: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_RELIGIOUS_BELIEFS, CONSENT)]      | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(religiousBeliefs: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_ORIENTATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(orientation: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_CITIZENSHIP_STATUS, CONSENT)]     | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(citizenshipStatus: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_HEALTH_INFO, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(healthInfo: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GENETIC_ID, CONSENT)]             | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geneticId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_BIOMETRIC_ID, CONSENT)]           | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(biometricId: 2))
        USP_UT_V1 | [new EqualityValueRule(SENSITIVE_DATA_GEOLOCATION, CONSENT)]            | new UspUtV1Consent.Builder()
                                                                                              .setSensitiveDataProcessing(new UsUtahSensitiveData(geolocation: 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(1, 2))
        USP_UT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspUtV1Consent.Builder().setKnownChildSensitiveDataConsents(0)

        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NOT_APPLICABLE),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NOT_APPLICABLE)]   | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 0, 0)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, CONSENT)]          | new UspCtV1Consent.Builder().setKnownChildSensitiveDataConsents(0, 2, 2)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), PBSUtils.getRandomNumber(0, 2), 1)
        USP_CT_V1 | [new EqualityValueRule(CHILD_CONSENTS_BELOW_13, NO_CONSENT),
                     new EqualityValueRule(CHILD_CONSENTS_FROM_13_TO_16, NO_CONSENT)]       | new UspCtV1Consent.Builder()
                                                                                              .setKnownChildSensitiveDataConsents(PBSUtils.getRandomNumber(0, 2), 1, PBSUtils.getRandomNumber(0, 2))
    }
}
