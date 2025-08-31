package org.prebid.server.functional.model.db

import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.ImpConfigTypeConverter
import org.prebid.server.functional.model.db.typeconverter.ProfileMergePrecedenceConvert
import org.prebid.server.functional.model.db.typeconverter.ProfileTypeConvert
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.profile.ImpProfile
import org.prebid.server.functional.model.request.profile.ProfileMergePrecedence
import org.prebid.server.functional.model.request.profile.ProfileType

@Entity
@Table(name = "profiles")
@ToString(includeNames = true)
class StoredProfileImp {

    @Id
    @Column(name = "profileId")
    String profileName
    @Column(name = "accountId")
    String accountId
    @Column(name = "mergePrecedence")
    @Convert(converter = ProfileMergePrecedenceConvert)
    ProfileMergePrecedence mergePrecedence
    @Column(name = "type")
    @Convert(converter = ProfileTypeConvert)
    ProfileType type
    @Column(name = "profile")
    @Convert(converter = ImpConfigTypeConverter)
    Imp impBody

    static StoredProfileImp getProfile(ImpProfile profile) {
        new StoredProfileImp().tap {
            it.profileName = profile.name
            it.accountId = profile.accountId
            it.mergePrecedence = profile.mergePrecedence
            it.type = profile.type
            it.impBody = profile.body
        }
    }
}
