package org.prebid.server.functional.model.db

import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.prebid.server.functional.model.db.typeconverter.StoredAuctionResponseConfigTypeConverter
import org.prebid.server.functional.model.db.typeconverter.StoredBidResponseConfigTypeConverter
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.SeatBid

import static jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "stored_responses")
@ToString(includeNames = true)
class StoredResponse {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id")
    Integer id
    @Column(name = "resId")
    String responseId
    @Column(name = "storedAuctionResponse")
    @Convert(converter = StoredAuctionResponseConfigTypeConverter)
    SeatBid storedAuctionResponse
    @Column(name = "storedBidResponse")
    @Convert(converter = StoredBidResponseConfigTypeConverter)
    BidResponse storedBidResponse
}
