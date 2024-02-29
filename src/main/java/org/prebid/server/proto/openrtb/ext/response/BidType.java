package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public enum BidType {

    banner,
    video,
    audio,
    @JsonProperty("native")
    xNative;

    public String getName() {
        return this == xNative ? "native" : this.name();
    }

    public static BidType fromString(String bidType) {
        try {
            return StringUtils.equals(bidType, "native") ? xNative : valueOf(bidType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
