package org.prebid.server.functional.model.db.typeconverter

import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.ObjectMapperWrapper

import javax.persistence.AttributeConverter

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
