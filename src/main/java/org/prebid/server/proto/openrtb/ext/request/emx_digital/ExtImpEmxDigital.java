package org.prebid.server.proto.openrtb.ext.request.emx_digital;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.emx_digital
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpEmxDigital {
    String tagid;

    String bidfloor;
}

