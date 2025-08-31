package org.prebid.server.functional.model.request.profile

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.util.PBSUtils

import static ProfileMergePrecedence.PROFILE

@ToString(includeNames = true, ignoreNulls = true)
class RequestProfile extends Profile<BidRequest> {

    static getProfile(String accountId = PBSUtils.randomString,
                      String name = PBSUtils.randomString,
                      ProfileMergePrecedence mergePrecedence = PROFILE) {
        BidRequest request = BidRequest.defaultBidRequest.tap {
            it.imp = null
            it.site = Site.configFPDSite
            it.device = Device.default
        }
        getProfile(accountId, request, name, mergePrecedence)
    }

    static getProfile(String accountId,
                      BidRequest request,
                      String name = PBSUtils.randomString,
                      ProfileMergePrecedence mergePrecedence = PROFILE) {

        new RequestProfile(accountId: accountId,
                name: name,
                type: ProfileType.REQUEST,
                mergePrecedence: mergePrecedence,
                body: request)
    }
}
