package org.prebid.server.functional.model.request.profile

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.PBSUtils

import static ProfileMergePrecedence.PROFILE

@ToString(includeNames = true, ignoreNulls = true)
class ImpProfile extends Profile<Imp> {

    static getProfile(String accountId,
                      Imp imp = Imp.defaultImpression,
                      String name = PBSUtils.randomString,
                      ProfileMergePrecedence mergePrecedence = PROFILE) {

        new ImpProfile(accountId: accountId,
                id: name,
                type: ProfileType.IMP,
                mergePrecedence: mergePrecedence,
                body: imp)
    }
}
