package com.iab.openrtb.response;

import lombok.Builder;
import lombok.Value;

/**
 * Corresponds to the Video Object in the request, yet containing a value of a conforming VAST tag as a value.
 */
@Builder
@Value
public class VideoObject {

    /**
     * Vast xml.
     */
    String vasttag;
}
