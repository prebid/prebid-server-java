package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.Dependencies

class StoredResponseConfigTypeConverter implements AttributeConverter<BidResponse, String> {

    @Override
    String convertToDatabaseColumn(BidResponse bidResponse) {
        bidResponse ? Dependencies.objectMapperWrapper.encode(bidResponse) : null
    }

    @Override
    BidResponse convertToEntityAttribute(String value) {
        value ? Dependencies.objectMapperWrapper.decode(value, BidResponse) : null
    }
}
