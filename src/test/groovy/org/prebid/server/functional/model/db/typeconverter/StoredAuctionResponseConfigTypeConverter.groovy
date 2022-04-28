package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.testcontainers.Dependencies

class StoredAuctionResponseConfigTypeConverter implements AttributeConverter<SeatBid, String> {

    @Override
    String convertToDatabaseColumn(SeatBid seatBid) {
        seatBid ? Dependencies.objectMapperWrapper.encode(seatBid) : null
    }

    @Override
    SeatBid convertToEntityAttribute(String value) {
        value ? Dependencies.objectMapperWrapper.decode(value, SeatBid) : null
    }
}
