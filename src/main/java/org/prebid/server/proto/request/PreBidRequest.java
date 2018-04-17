package org.prebid.server.proto.request;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class PreBidRequest {

    String accountId;

    /* Unique transaction ID. */
    String tid;

    /* Cache markup for two-phase response (get response then separate call to get markup). */
    Integer cacheMarkup;

    // Sorts bids by price & response time and returns ad server targeting keys for each bid in prebid server response
    Integer sortBids; // ... really just a boolean 0|1.

    // Used to determine whether ad server targeting key strings should be truncated on prebid server. For DFP max key
    // length should be 20.
    Integer maxKeyLength;

    /*
     * Flag to indicate if the impression requires secure HTTPS URL creative
     * assets and markup, where 0 = non-secure, 1 = secure. If omitted, the
     * secure state will be interpreted from the request to the prebid server.
     */
    Integer secure;  // ... really just a boolean 0|1.

    /* How long to wait for adapters to return bids. */
    Long timeoutMillis;

    List<AdUnit> adUnits;

    Boolean isDebug;

    /*
     * This object should be included if the ad supported content is a
     * non-browser application (typically in mobile) as opposed to a website. At
     * a minimum, it is useful to provide an App ID or bundle, but this is not
     * strictly required.
     */
    App app;

    /*
     * 3.2.18 Object: Device. This object provides information pertaining to
     * the device through which the user is interacting. Device information
     * includes its hardware, platform, location, and carrier data. The device
     * can refer to a mobile handset, a desktop computer, set top box, or other
     * digital device.
     */
    Device device;

    User user;

    Sdk sdk;
}
