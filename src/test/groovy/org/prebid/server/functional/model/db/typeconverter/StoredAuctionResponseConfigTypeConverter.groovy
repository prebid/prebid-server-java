package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.ObjectMapperWrapper

class StoredAuctionResponseConfigTypeConverter implements AttributeConverter<SeatBid, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(SeatBid seatBid) {
        seatBid ? encode(seatBid) : null
    }

    @Override
    SeatBid convertToEntityAttribute(String value) {
        value ? decode(value, SeatBid) : null
    }
}
