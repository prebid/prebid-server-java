package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.ObjectMapperWrapper

class StoredRequestConfigTypeConverter implements AttributeConverter<BidRequest, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(BidRequest bidRequest) {
        bidRequest ? encode(bidRequest) : null
    }

    @Override
    BidRequest convertToEntityAttribute(String value) {
        value ? decode(value, BidRequest) : null
    }
}
