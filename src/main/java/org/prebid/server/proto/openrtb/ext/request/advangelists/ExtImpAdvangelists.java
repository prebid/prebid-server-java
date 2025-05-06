package org.prebid.server.proto.openrtb.ext.request.advangelists;

import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.advangelists
 */
@Value(staticConstructor = "of")
public class ExtImpAdvangelists {

    String pubid;

    String placement;
}
