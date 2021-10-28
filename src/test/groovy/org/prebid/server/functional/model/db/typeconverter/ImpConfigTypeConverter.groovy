package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.testcontainers.Dependencies

class ImpConfigTypeConverter implements AttributeConverter<Imp, String> {

    @Override
    String convertToDatabaseColumn(Imp imp) {
        imp ? Dependencies.objectMapperWrapper.encode(imp) : null
    }

    @Override
    Imp convertToEntityAttribute(String value) {
        value ? Dependencies.objectMapperWrapper.decode(value, Imp) : null
    }
}
