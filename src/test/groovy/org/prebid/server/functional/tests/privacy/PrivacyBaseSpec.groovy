package org.prebid.server.functional.tests.privacy

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
import org.prebid.server.functional.util.privacy.ConsentString

@PBSTest
abstract class PrivacyBaseSpec extends BaseSpec {

    private static final int GEO_PRECISION = 2

    protected static BidRequest getBidRequestWithGeo() {
        BidRequest.defaultBidRequest.tap {
            device = new Device(geo: new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90), lon: PBSUtils.getFractionalRandomNumber(0, 90)))
        }
    }

    protected static BidRequest getCcpaBidRequest(ConsentString consentString) {
        bidRequestWithGeo.tap {
            regs.ext = new RegsExt(usPrivacy: consentString)
        }
    }

    protected static AmpRequest getCcpaAmpRequest(ConsentString consentString) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = consentString
            consentType = 3
        }
    }

    protected static BidRequest getGdprBidRequest(ConsentString consentString) {
        bidRequestWithGeo.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user = new User(ext: new UserExt(consent: consentString))
        }
    }

    protected static AmpRequest getGdprAmpRequest(ConsentString consentString) {
        AmpRequest.defaultAmpRequest.tap {
            gdprConsent = consentString
            consentType = 2
            gdprApplies = true
        }
    }

    protected static Geo getMaskedGeo(BidRequest bidRequest) {
        def geo = bidRequest.device.geo
        geo.lat = PBSUtils.getRoundFractionalNumber(geo.lat, GEO_PRECISION)
        geo.lon = PBSUtils.getRoundFractionalNumber(geo.lon, GEO_PRECISION)
        geo
    }
}
