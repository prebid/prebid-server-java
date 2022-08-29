package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;

/**
 * This object contains information known or derived about the human user of the
 * device (i.e., the audience for advertising). The user {@code id} is an
 * exchange artifact and may be subject to rotation or other privacy policies.
 * However, this user ID must be stable long enough to serve reasonably as the
 * basis for frequency capping and retargeting.
 */
@Builder(toBuilder = true)
@Value
public class User {

    /**
     * Exchange-specific ID for the user.
     */
    String id;

    /**
     * Buyer-specific ID for the user as mapped by the exchange for the buyer.
     */
    String buyeruid;

    /**
     * Year of birth as a 4-digit integer.
     */
    Integer yob;

    /**
     * Gender, where “M” = male, “F” = female, “O” = known to be other (i.e.,
     * omitted is unknown).
     */
    String gender;

    /**
     * Comma separated list of keywords, interests, or intent. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    String keywords;

    /**
     * Array of keywords about the user. Only one of ‘keywords’ or ‘kwarray’ may be present.
     */
    List<String> kwarray;

    /**
     * Optional feature to pass bidder data that was set in the exchange’s
     * cookie. The string must be in base85 cookie safe characters and be in any
     * format. Proper JSON encoding must be used to include “escaped” quotation
     * marks.
     */
    String customdata;

    /**
     * Location of the user’s home base defined by a {@link Geo} object (Section
     * 3.2.19). This is not necessarily their current location.
     */
    Geo geo;

    /**
     * Additional user data. Each {@link Data} object (Section 3.2.21) represents a
     * different data source.
     */
    List<Data> data;

    /**
     * When GDPR regulations are in effect this attribute contains
     * the Transparency and Consent Framework’s <a href="https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20Consent%20string%20and%20vendor%20list%20formats%20v2.md">Consent String</a>
     * data structure.
     */
    String consent;

    /**
     * Details for support of a standard protocol for multiple third party identity providers (Section 3.2.27).
     */
    List<Eid> eids;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtUser ext;
}
