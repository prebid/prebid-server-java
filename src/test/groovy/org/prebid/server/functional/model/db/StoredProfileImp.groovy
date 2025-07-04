package org.prebid.server.functional.model.db

import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.ImpConfigTypeConverter
import org.prebid.server.functional.model.db.typeconverter.ProfileMergePrecedenceConvert
import org.prebid.server.functional.model.db.typeconverter.ProfileTypeConvert
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.profile.ProfileImp
import org.prebid.server.functional.model.request.profile.ProfileMergePrecedence
import org.prebid.server.functional.model.request.profile.ProfileType

import static jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "profiles_profile")
@ToString(includeNames = true)
class StoredProfileImp {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "profileName")
    String profileRecordName
    @Column(name = "mergePrecedence")
    @Convert(converter = ProfileMergePrecedenceConvert)
    ProfileMergePrecedence mergePrecedence
    @Column(name = "profileType")
    @Convert(converter = ProfileTypeConvert)
    ProfileType type
    @Column(name = "profileBody")
    @Convert(converter = ImpConfigTypeConverter)
    Imp impBody

    static StoredProfileImp getProfile(ProfileImp profile) {
        new StoredProfileImp().tap {
            it.profileRecordName = profile.recordName
            it.mergePrecedence = profile.mergePrecedence
            it.type = profile.type
            it.impBody = profile.body
        }
    }
}
