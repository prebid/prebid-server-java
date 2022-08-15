package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.util.List;

/**
 * The top-level bid request object contains a globally unique bid request or
 * auction ID. This {@code id} attribute is required as is at least one
 * impression object (Section 3.2.4). Other attributes in this top-level object
 * establish rules and restrictions that apply to all impressions being offered.
 * <p>There are also several subordinate objects that provide detailed data to
 * potential buyers. Among these are the {@link Site} and {@link App} objects,
 * which describe the type of published media in which the impression(s) appear.
 * These objects are highly recommended, but only one applies to a given bid
 * request depending on whether the media is browser-based web content or a
 * non-browser application, respectively.
 */
@Builder(toBuilder = true)
@Value
public class BidRequest {

    /**
     * Unique ID of the bid request, provided by the exchange.
     * <p/> (required)
     */
    String id;

    /**
     * Array of {@link Imp} objects (Section 3.2.4) representing the impressions
     * offered. At least 1 {@link Imp}  object is required.
     */
    List<Imp> imp;

    /**
     * Details via a {@link Site} object (Section 3.2.13) about the publisher’s website.
     * Only applicable and recommended for websites.
     */
    Site site;

    /**
     * Details via an {@link App} object (Section 3.2.14) about the publisher’s app
     * (i.e., non-browser applications). Only applicable and recommended for
     * apps.
     */
    App app;

    /**
     * Details via a {@link Device} object (Section 3.2.18) about the user’s device
     * to which the impression will be delivered.
     */
    Device device;

    /**
     * Details via a {@link User} object (Section 3.2.20) about the human user
     * of the device; the advertising audience.
     */
    User user;

    /**
     * Indicator of test mode in which auctions are not billable,
     * where 0 = live mode, 1 = test mode.
     */
    Integer test;

    /**
     * Auction type, where 1 = First Price, 2 = Second Price Plus. Exchange-specific
     * auction types can be defined using values 500 and greater.
     */
    Integer at;

    /**
     * Maximum time in milliseconds the exchange allows for bids to be received
     * including Internet latency to avoid timeout. This value supersedes any
     * <em>a priori</em> guidance from the exchange.
     */
    Long tmax;

    /**
     * Allowed list of buyer seats (e.g., advertisers, agencies) allowed to bid on
     * this impression. IDs of seats and knowledge of the buyer’s customers to
     * which they refer must be coordinated between bidders and the exchange
     * <em>a priori</em>. At most, only one of wseat and bseat should be used in
     * the same request. Omission of both implies no seat restrictions.
     */
    List<String> wseat;

    /**
     * Block list of buyer seats (e.g., advertisers, agencies) restricted from
     * bidding on this impression. IDs of seats and knowledge of the buyer’s
     * customers to which they refer must be coordinated between bidders and the
     * exchange <em>a priori</em>. At most, only one of wseat and bseat should
     * be used in the same request. Omission of both implies no seat
     * restrictions.
     */
    List<String> bseat;

    /**
     * Flag to indicate if Exchange can verify that the impressions offered
     * represent all of the impressions available in context (e.g., all on the
     * web page, all video spots such as pre/mid/post roll) to support
     * road-blocking. 0 = no or unknown, 1 = yes, the impressions offered
     * represent all that are available.
     */
    Integer allimps;

    /**
     * Array of allowed currencies for bids on this bid request using ISO-4217
     * alpha codes. Recommended only if the exchange accepts multiple
     * currencies.
     */
    List<String> cur;

    /**
     * Allowed list of languages for creatives using ISO-639-1-alpha-2.
     * Omission implies no specific restrictions, but buyers would be
     * advised to consider language attribute in the {@link Device} and/or
     * {@link Content} objects if available. Only one of wlang or wlangb
     * should be present.
     */
    List<String> wlang;

    /**
     * Allowed list of languages for creatives using IETF BCP 47I.
     * Omission implies no specific restrictions, but buyers would be
     * advised to consider language attribute in the {@link Device} and/or
     * {@link Content} objects if available. Only one of wlang or wlangb
     * should be present.
     */
    List<String> wlangb;

    /**
     * Blocked advertiser categories using the specified category taxonomy. <p/>
     * The taxonomy to be used is defined by the cattax field. If no cattax
     * field is supplied IAB Content Category Taxonomy 1.0 is assumed.
     */
    List<String> bcat;

    /**
     * The taxonomy in use for bcat. Refer to the AdCOM
     * 1.0 list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category Taxonomies</a> for values.
     */
    Integer cattax;

    /**
     * Block list of advertisers by their domains (e.g., “ford.com”).
     */
    List<String> badv;

    /**
     * Block list of applications by their app store IDs. See <a href="https://iabtechlab.com/wp-content/uploads/2020/08/IAB-Tech-Lab-OTT-store-assigned-App-Identification-Guidelines-2020.pdf">
     * OTT/CTV Store Assigned App Identification Guidelines</a> for more
     * details about expected strings for CTV app stores. For mobile apps
     * in Google Play Store, these should be bundle or package names (e.g.com.foo.mygame).
     * For apps in Apple App Store, these should be a numeric ID.
     */
    List<String> bapp;

    /**
     * A {@link Source} object (Section 3.2.2) that provides data about the inventory
     * source and which entity makes the final decision.
     */
    Source source;

    /**
     * A {@link Regs} object (Section 3.2.3) that specifies any industry, legal,
     * or governmental regulations in force for this request.
     */
    Regs regs;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB
     */
    ExtRequest ext;
}
