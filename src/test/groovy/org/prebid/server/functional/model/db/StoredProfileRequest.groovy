package org.prebid.server.functional.model.db

import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.ProfileMergePrecedenceConvert
import org.prebid.server.functional.model.db.typeconverter.ProfileTypeConvert
import org.prebid.server.functional.model.db.typeconverter.BidRequestConfigTypeConverter
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.profile.ProfileMergePrecedence
import org.prebid.server.functional.model.request.profile.RequestProfile
import org.prebid.server.functional.model.request.profile.ProfileType

@Entity
@Table(name = "profiles")
@ToString(includeNames = true)
class StoredProfileRequest {

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
    @Convert(converter = BidRequestConfigTypeConverter)
    BidRequest requestBody

    static StoredProfileRequest getProfile(RequestProfile profile) {
        new StoredProfileRequest().tap {
            it.profileName = profile.id
            it.accountId = profile.accountId
            it.mergePrecedence = profile.mergePrecedence
            it.type = profile.type
            it.requestBody = profile.body
        }
    }
}
