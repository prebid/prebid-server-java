package org.prebid.server.proto.openrtb.ext.request.emxdigital;

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

