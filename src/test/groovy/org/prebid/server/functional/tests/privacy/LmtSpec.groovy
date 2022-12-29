package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceExt
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

class LmtSpec extends BaseSpec {

    private static final BUGGED_IFA_VALUES = [null, "", "00000000-0000-0000-0000-000000000000"]

    def "PBS should set device.lmt = 1 when device.osv = '#osv' and device.ifa = '#ifa' for iOS app requests"() {
        given: "Default app BidRequest with device.os = iOS and any device.ext.atts"
        def device = new Device().tap {
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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
            it.os = PBSUtils.randomizeCase("iOS")
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

    private static getRandomAtts() {
        PBSUtils.getRandomElement(DeviceExt.Atts.values() as List<DeviceExt.Atts>)
    }

    private static getRandomLmt() {
        PBSUtils.getRandomNumber(0, 1)
    }
}
