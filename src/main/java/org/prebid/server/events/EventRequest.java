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

    String accountId;

    Format format;

    Analytics analytics;

    public enum Type {

        win, imp
    }

    public enum Format {

        blank, image
    }

    public enum Analytics {

        enabled, disabled
    }
}
