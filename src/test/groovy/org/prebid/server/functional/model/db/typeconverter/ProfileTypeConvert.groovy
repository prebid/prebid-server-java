package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.request.profile.ProfileType

class ProfileTypeConvert implements AttributeConverter<ProfileType, String> {

    @Override
    String convertToDatabaseColumn(ProfileType profileMergePrecedence) {
        profileMergePrecedence?.value
    }

    @Override
    ProfileType convertToEntityAttribute(String value) {
        value ? ProfileType.forValue(value) : null
    }
}
