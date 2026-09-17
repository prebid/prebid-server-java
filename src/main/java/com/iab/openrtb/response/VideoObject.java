package com.iab.openrtb.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * Corresponds to the Video Object in the request, yet containing a value of a conforming VAST tag as a value.
 */
@Builder
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoObject {

    /**
     * Vast xml.
     */
    String vasttag;
}
