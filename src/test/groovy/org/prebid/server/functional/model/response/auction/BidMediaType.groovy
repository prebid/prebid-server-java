package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.request.auction.BidAdjustmentMediaType

enum BidMediaType {

    BANNER(1),
    VIDEO(2),
    AUDIO(3),
    NATIVE(4)

    @JsonValue
    final Integer value

    BidMediaType(Integer value) {
        this.value = value
    }

    static BidMediaType from(BidAdjustmentMediaType mediaType) {
        return switch (mediaType) {
            case BidAdjustmentMediaType.BANNER -> BANNER
            case BidAdjustmentMediaType.VIDEO -> VIDEO
            case BidAdjustmentMediaType.VIDEO_IN_STREAM -> VIDEO
            case BidAdjustmentMediaType.VIDEO_OUT_STREAM -> VIDEO
            case BidAdjustmentMediaType.AUDIO -> AUDIO
            case BidAdjustmentMediaType.NATIVE -> NATIVE
            default -> throw new IllegalArgumentException("Unknown media type: " + mediaType);
        };
    }
}
