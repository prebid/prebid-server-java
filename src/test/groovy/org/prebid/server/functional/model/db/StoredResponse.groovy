package org.prebid.server.functional.model.db

import groovy.transform.ToString
import org.prebid.server.functional.model.db.typeconverter.StoredAuctionResponseConfigTypeConverter
import org.prebid.server.functional.model.db.typeconverter.StoredBidResponseConfigTypeConverter
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.SeatBid

import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

import static javax.persistence.GenerationType.IDENTITY

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
