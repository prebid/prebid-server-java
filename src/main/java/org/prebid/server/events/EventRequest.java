package org.prebid.server.events;

import lombok.Builder;
import lombok.Value;

/**
 * Represents event request.
 */
@Builder
@Value
public class EventRequest {

    Type type;

    String bidId;

    String auctionId;

    String accountId;

    String bidder;

    Long timestamp;

    Format format;

    String integration;

    Analytics analytics;

    String lineItemId;

    VastType vType;

    public enum Type {

        win, imp, vast
    }

    public enum Format {

        blank, image
    }

    public enum Analytics {

        enabled, disabled
    }

    public enum VastType {

        START("start"),
        FIRST_QUARTILE("firstQuartile"),
        MID_POINT("midPoint"),
        THIRD_QUARTILE("thirdQuartile"),
        COMPLETE("complete");

        private final String name;

        VastType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
