package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

/**
 * This object represents an allowed size (i.e., height and width combination)
 * or Flex Ad parameters for a banner impression. These are typically used in an
 * array where multiple sizes are permitted. It is recommended that either the
 * {@code w}/{@code h} pair or the {@code wratio}/{@code hratio}/{@code wmin}
 * set (i.e., for Flex Ads) be specified.
 */
@Builder
@Value
public class Format {

    /**
     * Width in device independent pixels (DIPS).
     */
    Integer w;

    /**
     * Height in device independent pixels (DIPS).
     */
    Integer h;

    /**
     * Relative width when expressing size as a ratio.
     */
    Integer wratio;

    /**
     * Relative height when expressing size as a ratio.
     */
    Integer hratio;

    /**
     * The minimum width in device independent pixels (DIPS) at which the ad
     * will be displayed the size is expressed as a ratio.
     */
    Integer wmin;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
