package org.rtb.vexing.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Map;

/**
 * Bid is a bid from the auction. These are produced by Adapters, and target a particular Ad Unit.
 * <p>
 * This JSON format is a contract with both Prebid.js and Prebid-mobile.
 * All changes *must* be backwards compatible, since clients cannot be forced to update their code.
 */
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class Bid {

    // Identifies the Bid Request within the Ad Unit which this Bid targets. It should match one of
    // the values inside PreBidRequest.adUnits[i].bids[j].bidId.
    String bidId;

    // Identifies the AdUnit which this Bid targets.
    // It should match one of PreBidRequest.adUnits[i].code, where "i" matches the AdUnit used in
    // as bidId.
    String code;

    // Uniquely identifies the creative being served. It is not used by prebid-server, but
    // it helps publishers and bidders identify and communicate about malicious or inappropriate ads.
    // This project simply passes it along with the bid.
    String creativeId;

    // Shows whether the creative is a video or banner.
    String mediaType;

    // Bidder.bidderCode of the Bidder who made this bid.

    String bidder;

    // Hash of the bidder's unique bid identifier for blockchain. It should not be sent to browser.
    @JsonIgnore
    String bidHash;

    // Cpm, in US Dollars, which the bidder is willing to pay if this bid is chosen.
    // TODO: Add support for other currencies someday.
    Float price;

    // URL which returns ad markup, and should be called if the bid wins.
    // If NURL and Adm are both defined, then Adm takes precedence.
    String nurl;

    // Ad markup which should be used to deliver the ad, if this bid is chosen.
    // If NURL and Adm are both defined, then Adm takes precedence.
    String adm;

    // Intended width which Adm should be shown, in pixels.
    Integer width;

    // Intended width which Adm should be shown, in pixels.
    Integer height;

    // Not used by prebid-server, but may be used by buyers and sellers who make special
    // deals with each other. We simply pass this information along with the bid.
    String dealId;

    // ID in prebid-cache which can be used to fetch this ad's content.
    // This supports prebid-mobile, which requires that the content be available from a URL.

    String cacheId;

    // Number of milliseconds it took for the adapter to return a bid.
    Integer responseTime;

    Map<String, String> adServerTargeting;
}
