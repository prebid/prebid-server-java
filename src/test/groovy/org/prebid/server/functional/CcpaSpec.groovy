package org.prebid.server.functional

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

@PBSTest
class CcpaSpec extends BaseSpec {

    // TODO: extend ccpa test with actual fields that we should mask
    def "PBS should mask publisher info when privacy.ccpa.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def valid_ccpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: valid_ccpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: true)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundFractionalNumber(lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundFractionalNumber(lon, 2)
    }

    // TODO: extend this ccpa test with actual fields that we should mask
    def "PBS should not mask publisher info when privacy.ccpa.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def valid_ccpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: valid_ccpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: false)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == lat
        assert bidderRequests.device?.geo?.lon == lon
    }
}
