package org.prebid.server.functional.model.request.profile

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils

import static ProfileMergePrecedence.PROFILE

@ToString(includeNames = true, ignoreNulls = true)
class ProfileRequest extends Profile<BidRequest> {

    static getProfile(String accountId,
                      BidRequest request = BidRequest.defaultBidRequest.tap { imp = null },
                      String name = PBSUtils.randomString,
                      ProfileMergePrecedence mergePrecedence = PROFILE) {

        new ProfileRequest(accountId: accountId,
                name: name,
                type: ProfileType.REQUEST,
                mergePrecedence: mergePrecedence,
                body: request)
    }
}
