package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ACCOUNT_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class LmtSpec extends PrivacyBaseSpec {

    private static final BUGGED_IFA_VALUES = [null, "", "00000000-0000-0000-0000-000000000000"]

    def "PBS should set device.lmt = 1 when device.osv = '#osv' and device.ifa = '#ifa' for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.ifa = ifa
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1

        where:
        osv    || ifa
        "14.0" || null
        "14.1" || null
        "14.0" || ""
        "14.1" || ""
        "14.0" || "00000000-0000-0000-0000-000000000000"
        "14.1" || "00000000-0000-0000-0000-000000000000"
    }

    def "PBS should override device.lmt to 1 when device.osv = '#osv' and device.ifa has bugged value for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = lmt
            it.ifa = PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1

        where:
        osv    || lmt
        "14.0" || 0
        "14.0" || 1
        "14.1" || 0
        "14.1" || 1
    }

    def "PBS should set device.lmt = 0 when device.osv = '#osv' and device.ifa is populated for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.ifa = PBSUtils.randomString
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 0

        where:
        osv << ["14.0", "14.1"]
    }

    def "PBS should override device.lmt to 0 when device.osv = '#osv' and device.ifa is populated for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = lmt
            it.ifa = PBSUtils.randomString
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 0

        where:
        osv    || lmt
        "14.0" || 0
        "14.0" || 1
        "14.1" || 0
        "14.1" || 1
    }

    def "PBS should set device.lmt = 0 when device.osv >= 14.2 and device.ext.atts = 3 for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and device.ext.atts = 3"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.ifa = ifa
            it.ext = new DeviceExt(atts: DeviceExt.Atts.AUTHORIZED)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 0

        where:
        osv    || ifa
        "14.2" || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || PBSUtils.randomString
        "15.0" || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || PBSUtils.randomString
    }

    def "PBS should override device.lmt to 0 when device.osv >= 14.2 and device.ext.atts = 3 for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and device.ext.atts = 3"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = randomLmt
            it.ifa = ifa
            it.ext = new DeviceExt(atts: DeviceExt.Atts.AUTHORIZED)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 0

        where:
        osv    || ifa
        "14.2" || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || PBSUtils.randomString
        "15.0" || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || PBSUtils.randomString
    }

    def "PBS should set device.lmt = 1 when device.osv >= 14.2 and device.ext.atts = '#atts' for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and device.ext.atts != 3"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.ifa = ifa
            it.ext = new DeviceExt(atts: atts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1

        where:
        osv    || atts                      || ifa
        "14.2" || DeviceExt.Atts.UNKNOWN    || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.UNKNOWN    || PBSUtils.randomString
        "14.2" || DeviceExt.Atts.RESTRICTED || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.RESTRICTED || PBSUtils.randomString
        "14.2" || DeviceExt.Atts.DENIED     || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.DENIED     || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.UNKNOWN    || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.UNKNOWN    || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.RESTRICTED || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.RESTRICTED || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.DENIED     || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.DENIED     || PBSUtils.randomString
    }

    def "PBS should override device.lmt to 1 when device.osv >= 14.2 and device.ext.atts = '#atts' for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and device.ext.atts != 3"
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = randomLmt
            it.ifa = ifa
            it.ext = new DeviceExt(atts: atts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == 1

        where:
        osv    || atts                      || ifa
        "14.2" || DeviceExt.Atts.UNKNOWN    || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.UNKNOWN    || PBSUtils.randomString
        "14.2" || DeviceExt.Atts.RESTRICTED || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.RESTRICTED || PBSUtils.randomString
        "14.2" || DeviceExt.Atts.DENIED     || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "14.2" || DeviceExt.Atts.DENIED     || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.UNKNOWN    || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.UNKNOWN    || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.RESTRICTED || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.RESTRICTED || PBSUtils.randomString
        "15.0" || DeviceExt.Atts.DENIED     || PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
        "15.0" || DeviceExt.Atts.DENIED     || PBSUtils.randomString
    }

    def "PBS should not modify device.lmt when device.os != iOS"() {
        given: "Default app BidRequest with non-iOS device.os"
        def lmt = randomLmt
        def device = new Device().tap {
            it.os = PBSUtils.randomString
            it.osv = osv
            it.lmt = lmt
            it.ifa = PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == lmt

        where:
        osv << ["14.0", "14.1", "14.2", "15.0"]
    }

    def "PBS shouldn't modify device.lmt when BidRequest doesn't contain app"() {
        given: "Default non-app BidRequest with iOS device.os"
        def lmt = randomLmt
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = lmt
            it.ifa = PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(SITE).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == lmt

        where:
        osv << ["14.0", "14.1", "14.2", "15.0"]
    }

    def "PBS shouldn't modify device.lmt when device.osv < 14.0"() {
        given: "Default non-app BidRequest with non-iOS device.os"
        def lmt = randomLmt
        def device = new Device().tap {
            it.os = PBSUtils.getRandomCase("iOS")
            it.osv = osv
            it.lmt = lmt
            it.ifa = PBSUtils.getRandomElement(BUGGED_IFA_VALUES)
            it.ext = new DeviceExt(atts: randomAtts)
        }
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            it.device = device
        }

        when: "PBS processes BidRequest"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "LMT should be set to 1 in the bidder call"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.device.lmt == lmt

        where:
        osv << ["13.0", "12.0", "11.0"]
    }

    def "PBS auction should mask device and user fields for auction request when device.lm = 1 was passed"() {
        given: "BidRequest with personal data"
        def bidRequest = bidRequestWithPersonalData.tap {
            device.lmt = 1
        }

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
        }

        and: "Bidder request should mask device personal data"
        verifyAll (bidderRequest.device) {
            !didsha1
            !didmd5
            !dpidsha1
            !ifa
            !macsha1
            !macmd5
            !dpidmd5
            !geo.metro
            !geo.city
            !geo.zip
            !geo.accuracy
            !geo.ipservice
            !geo.ext
        }

        and: "Bidder request should mask user personal data"
        verifyAll (bidderRequest.user) {
            !id
            !buyeruid
            !yob
            !gender
            !eids
            !data
            !geo
            !ext
            !eids
            !ext?.eids
        }

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
    }

    def "PBS auction shouldn't mask device and user fields for auction request when device.lm = 0 was passed"() {
        given: "BidRequest with personal data"
        def bidRequest = bidRequestWithPersonalData.tap {
            device.lmt = 0
        }

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.device.didsha1 == bidRequest.device.didsha1
            bidderRequest.device.didmd5 == bidRequest.device.didmd5
            bidderRequest.device.dpidsha1 == bidRequest.device.dpidsha1
            bidderRequest.device.ifa == bidRequest.device.ifa
            bidderRequest.device.macsha1 == bidRequest.device.macsha1
            bidderRequest.device.macmd5 == bidRequest.device.macmd5
            bidderRequest.device.dpidmd5 == bidRequest.device.dpidmd5
            bidderRequest.device.ip == bidRequest.device.ip
            bidderRequest.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon
            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
            bidderRequest.device.geo.metro == bidRequest.device.geo.metro
            bidderRequest.device.geo.city == bidRequest.device.geo.city
            bidderRequest.device.geo.zip == bidRequest.device.geo.zip
            bidderRequest.device.geo.accuracy == bidRequest.device.geo.accuracy
            bidderRequest.device.geo.ipservice == bidRequest.device.geo.ipservice
            bidderRequest.device.geo.ext == bidRequest.device.geo.ext

            bidderRequest.user.id == bidRequest.user.id
            bidderRequest.user.buyeruid == bidRequest.user.buyeruid
            bidderRequest.user.yob == bidRequest.user.yob
            bidderRequest.user.gender == bidRequest.user.gender
            bidderRequest.user.eids[0].source == bidRequest.user.eids[0].source
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.geo.lat == bidRequest.user.geo.lat
            bidderRequest.user.geo.lon == bidRequest.user.geo.lon
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
    }

    def "PBS amp should mask device and user fields for auction request when device.lm = 1 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest =bidRequestWithPersonalData.tap {
            device.lmt = 1
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(ampStoredRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == ampStoredRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == ampStoredRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == ampStoredRequest.device.geo.country
            bidderRequest.device.geo.region == ampStoredRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == ampStoredRequest.device.geo.utcoffset
        }

        and: "Bidder request should mask device personal data"
        verifyAll (bidderRequest.device) {
            !didsha1
            !didmd5
            !dpidsha1
            !ifa
            !macsha1
            !macmd5
            !dpidmd5
            !geo.metro
            !geo.city
            !geo.zip
            !geo.accuracy
            !geo.ipservice
            !geo.ext
        }

        and: "Bidder request should mask user personal data"
        verifyAll (bidderRequest.user) {
            !id
            !buyeruid
            !yob
            !gender
            !eids
            !data
            !geo
            !ext
            !eids
            !ext?.eids
        }

        and: "Metrics processed across activities should be updated"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)] == 1
    }

    def "PBS amp shouldn't mask device and user fields for auction request when device.lm = 0 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest =bidRequestWithPersonalData.tap {
            device.lmt = 0
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metric"
        flushMetrics(defaultPbsService)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(ampStoredRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            bidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            bidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            bidderRequest.device.ifa == ampStoredRequest.device.ifa
            bidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            bidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            bidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            bidderRequest.device.ip == ampStoredRequest.device.ip
            bidderRequest.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequest.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequest.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequest.device.geo.country == ampStoredRequest.device.geo.country
            bidderRequest.device.geo.region == ampStoredRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == ampStoredRequest.device.geo.utcoffset
            bidderRequest.device.geo.metro == ampStoredRequest.device.geo.metro
            bidderRequest.device.geo.city == ampStoredRequest.device.geo.city
            bidderRequest.device.geo.zip == ampStoredRequest.device.geo.zip
            bidderRequest.device.geo.accuracy == ampStoredRequest.device.geo.accuracy
            bidderRequest.device.geo.ipservice == ampStoredRequest.device.geo.ipservice
            bidderRequest.device.geo.ext == ampStoredRequest.device.geo.ext

            bidderRequest.user.id == ampStoredRequest.user.id
            bidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            bidderRequest.user.yob == ampStoredRequest.user.yob
            bidderRequest.user.gender == ampStoredRequest.user.gender
            bidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            bidderRequest.user.data == ampStoredRequest.user.data
            bidderRequest.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequest.user.geo.lon == ampStoredRequest.user.geo.lon
            bidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)]
    }

    private static getRandomAtts() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

    private static getRandomLmt() {
        PBSUtils.getRandomNumber(0, 1)
    }
}
