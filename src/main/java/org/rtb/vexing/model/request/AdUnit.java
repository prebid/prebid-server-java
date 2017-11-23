package org.rtb.vexing.model.request;

import com.iab.openrtb.request.Format;
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
public final class AdUnit {

    // --- REQUIRED ---

    /* Unique code of the ad unit on the page. */
    String code;

    List<Format> sizes;

    // --- One of the following two is required. ---

    List<Bid> bids;

    /* The configuration to load for this ad unit. */
    String configId;

    // --- NOT REQUIRED ---

    /* Whether this ad will render in the top IFRAME. */
    Integer topframe;  // ... really just a boolean 0|1.

    /* 1 = the ad is interstitial or full screen, 0 = not interstitial. */
    Integer instl;  // ... really just a boolean 0|1.
}
