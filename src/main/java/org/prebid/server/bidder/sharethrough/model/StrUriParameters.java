package org.prebid.server.bidder.sharethrough.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class StrUriParameters {
    String pkey;

    String bidID;

    boolean consentRequired;

    String consentString;

    boolean instantPlayCapable;

    boolean iframe;

    int height;

    int width;

}
