package org.prebid.server.functional.model.db.typeconverter

import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.ObjectMapperWrapper

import javax.persistence.AttributeConverter

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
