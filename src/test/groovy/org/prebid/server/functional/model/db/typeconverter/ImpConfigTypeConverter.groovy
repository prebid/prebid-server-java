package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.ObjectMapperWrapper

class ImpConfigTypeConverter implements AttributeConverter<Imp, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(Imp imp) {
        imp ? encode(imp) : null
    }

    @Override
    Imp convertToEntityAttribute(String value) {
        value ? decode(value, Imp) : null
    }
}
