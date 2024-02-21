package org.prebid.server.functional.model.db.typeconverter

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.ObjectMapperWrapper

import javax.persistence.AttributeConverter

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
