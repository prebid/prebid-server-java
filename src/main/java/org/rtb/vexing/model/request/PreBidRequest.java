package org.rtb.vexing.model.request;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class PreBidRequest {

    String accountId;

    // FIXME Ensure there is at least one.
    List<AdUnit> adUnits;

    // --- NOT REQUIRED ---

    /* Unique transaction ID. */
    String tid;

    /* How long to wait for adapters to return bids. */
    Long timeoutMillis;

    /*
     * Flag to indicate if the impression requires secure HTTPS URL creative
     * assets and markup, where 0 = non-secure, 1 = secure. If omitted, the
     * secure state will be interpreted from the request to the prebid server.
     */
    // FIXME Check "X-Forwarded-Proto" header for "https" or if TLS used on request.
    Integer secure;  // ... really just a boolean 0|1.

    /* Cache markup for two-phase response (get response then separate call to get markup). */
    Integer cacheMarkup;

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

    Boolean isDebug;
}
