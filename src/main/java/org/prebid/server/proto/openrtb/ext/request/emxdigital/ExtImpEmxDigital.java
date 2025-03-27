package org.prebid.server.proto.openrtb.ext.request.emxdigital;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.emx_digital
 */
@Value(staticConstructor = "of")
public class ExtImpEmxDigital {

    String tagid;

    String bidfloor;
}
