package com.iab.openrtb.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * This object describes an ad placement or impression being auctioned. A single
 * bid request can include multiple {@link Imp} objects, a use case for which
 * might be an exchange that supports selling all ad positions on a given page.
 * Each {@link Imp} object has a required ID so that bids can reference them
 * individually.
 * <p>The presence of {@link Banner} (Section 3.2.6),
 * {@link Video} (Section 3.2.7), and/or {@link Native} (Section 3.2.9) objects
 * subordinate to the Imp object indicates the type of impression being offered.
 * The publisher can choose one such type which is the typical case or mix them
 * at their discretion. However, any given bid for the impression must conform
 * to one of the offered types.
 */
@Builder(toBuilder = true)
@Value
public class Imp {

    /**
     * A unique identifier for this impression within the context of the bid
     * request (typically, starts with 1 and increments.
     * (required)
     */
    String id;

    /**
     * A Banner object (Section 3.2.6); required if this impression is offered
     * as a banner ad opportunity.
     */
    Banner banner;

    /** An array of Metric object (Section 3.2.5). */
    List<Metric> metric;

    /**
     * A Video object (Section 3.2.7); required if this impression is offered as
     * a video ad opportunity.
     */
    Video video;

    /**
     * An Audio object (Section 3.2.8); required if this impression is offered
     * as an audio ad opportunity.
     */
    Audio audio;

    /**
     * A Native object (Section 3.2.9); required if this impression is offered
     * as a native ad opportunity.
     */
    @JsonProperty("native")
    @Getter(onMethod = @__({@JsonProperty("native")}))
    Native xNative;

    /**
     * A Pmp object (Section 3.2.11) containing any private marketplace deals in
     * effect for this impression.
     */
    Pmp pmp;

    /**
     * Name of ad mediation partner, SDK technology, or player responsible for
     * rendering ad (typically video or mobile). Used by some ad servers to
     * customize ad code by partner. Recommended for video and/or apps.
     */
    String displaymanager;

    /**
     * Version of ad mediation partner, SDK technology, or player responsible
     * for rendering ad (typically video or mobile). Used by some ad servers to
     * customize ad code by partner. Recommended for video and/or apps.
     */
    String displaymanagerver;

    /** 1 = the ad is interstitial or full screen, 0 = not interstitial. */
    Integer instl;

    /**
     * Identifier for specific ad placement or ad tag that was used to initiate
     * the auction. This can be useful for debugging of any issues, or for
     * optimization by the buyer.
     */
    String tagid;

    /** Minimum bid for this impression expressed in CPM. */
    BigDecimal bidfloor;

    /**
     * Currency specified using ISO-4217 alpha codes. This may be different from
     * bid currency returned by bidder if this is allowed by the exchange.
     */
    String bidfloorcur;

    /**
     * Indicates the type of browser opened upon clicking the creative in an
     * app, where 0 = embedded, 1 = native. Note that the Safari View Controller
     * in iOS 9.x devices is considered a native browser for purposes of this
     * attribute.
     */
    Integer clickbrowser;

    /**
     * Flag to indicate if the impression requires secure HTTPS URL creative
     * assets and markup, where 0 = non-secure, 1 = secure. If omitted, the
     * secure state is unknown, but non-secure HTTP support can be assumed.
     */
    Integer secure;

    /** Array of exchange-specific names of supported iframe busters. */
    List<String> iframebuster;

    /**
     * Advisory as to the number of seconds that may elapse between the auction
     * and the actual impression.
     */
    Integer exp;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
