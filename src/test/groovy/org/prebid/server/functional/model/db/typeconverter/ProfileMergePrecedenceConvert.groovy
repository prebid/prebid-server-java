package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.request.profile.ProfileMergePrecedence
import org.prebid.server.functional.util.ObjectMapperWrapper

class ProfileMergePrecedenceConvert implements AttributeConverter<ProfileMergePrecedence, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(ProfileMergePrecedence profileMergePrecedence) {
        profileMergePrecedence?.value
    }

    @Override
    ProfileMergePrecedence convertToEntityAttribute(String value) {
        value ? ProfileMergePrecedence.forValue(value) : null
    }
}
