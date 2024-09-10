package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.ObjectMapperWrapper

class StoredBidResponseConfigTypeConverter implements AttributeConverter<BidResponse, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(BidResponse bidResponse) {
        bidResponse ? encode(bidResponse) : null
    }

    @Override
    BidResponse convertToEntityAttribute(String value) {
        value ? decode(value, BidResponse) : null
    }
}
