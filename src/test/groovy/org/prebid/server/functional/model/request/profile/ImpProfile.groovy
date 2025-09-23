package org.prebid.server.functional.model.request.profile

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.PBSUtils

import static ProfileMergePrecedence.PROFILE

@ToString(includeNames = true, ignoreNulls = true)
class ImpProfile extends Profile<Imp> {

    static ImpProfile getProfile(String accountId = PBSUtils.randomNumber.toString(),
                                 Imp imp = Imp.defaultImpression,
                                 String name = PBSUtils.randomString,
                                 ProfileMergePrecedence mergePrecedence = PROFILE) {

        new ImpProfile().tap {
            it.accountId = accountId
            it.id = name
            it.type = ProfileType.IMP
            it.mergePrecedence = mergePrecedence
            it.body = imp
            it.accountId = accountId
        }
    }
}
