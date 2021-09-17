package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
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
