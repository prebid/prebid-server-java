package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtUserTime {

    /**
     * Day of week.
     * 1=sun, 7=sat
     */
    Integer userdow;

    /**
     * Hour of day.
     * 0-23
     */
    Integer userhour;
}
