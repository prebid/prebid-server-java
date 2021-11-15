package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

@PBSTest
abstract class PrivacyBaseSpec extends BaseSpec {

    private static int GEO_PRECISION = 2

    protected BidRequest getBidRequestWithGeo() {
        BidRequest.defaultBidRequest.tap {
            device = new Device(geo: new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90), lon: PBSUtils.getFractionalRandomNumber(0, 90)))
        }
    }

    protected BidRequest getCcpaBidRequest(String usPrivacy) {
        bidRequestWithGeo.tap {
            regs.ext = new RegsExt(usPrivacy: usPrivacy)
        }
    }

    protected AmpRequest getCcpaAmpRequest(String usPrivacy) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = usPrivacy
            consentType = 3
        }
    }

    protected BidRequest getGdprBidRequest(String consentString) {
        bidRequestWithGeo.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user = new User(ext: new UserExt(consent: consentString))
        }
    }

    protected AmpRequest getGdprAmpRequest(String consentString) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = consentString
            consentType = 2
            gdprApplies = true
        }
    }

    protected Map<String, Float> getMaskedGeo(BidderRequest bidderRequest) {
        [lat: PBSUtils.getRoundFractionalNumber(bidderRequest.device.geo.lat, GEO_PRECISION),
         lon: PBSUtils.getRoundFractionalNumber(bidderRequest.device.geo.lon, GEO_PRECISION)]
    }
}
