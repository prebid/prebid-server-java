package org.prebid.server.proto.request.legacy;

import com.iab.openrtb.request.Format;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Deprecated
@Builder
@Value
public class AdUnit {

    /* Unique code of the ad unit on the page. */
    String code;

    List<Format> sizes;

    // --- One of the following two is required. ---

    List<Bid> bids;

    /* The configuration to load for this ad unit. */
    String configId;

    /* Whether this ad will render in the top IFRAME. */
    Integer topframe;  // ... really just a boolean 0|1.

    /* 1 = the ad is interstitial or full screen, 0 = not interstitial. */
    Integer instl;  // ... really just a boolean 0|1.

    List<String> mediaTypes;

    Video video;
}
