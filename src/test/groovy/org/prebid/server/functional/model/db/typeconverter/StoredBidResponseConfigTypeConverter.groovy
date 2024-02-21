package org.prebid.server.functional.model.db.typeconverter

import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.ObjectMapperWrapper

import javax.persistence.AttributeConverter

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
