package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.testcontainers.Dependencies

class StoredRequestConfigTypeConverter implements AttributeConverter<BidRequest, String> {

    @Override
    String convertToDatabaseColumn(BidRequest bidRequest) {
        bidRequest ? Dependencies.objectMapperWrapper.encode(bidRequest) : null
    }

    @Override
    BidRequest convertToEntityAttribute(String value) {
        value ? Dependencies.objectMapperWrapper.decode(value, BidRequest) : null
    }
}
