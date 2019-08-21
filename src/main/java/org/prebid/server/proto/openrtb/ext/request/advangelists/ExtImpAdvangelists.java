package org.prebid.server.proto.openrtb.ext.request.advangelists;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.advangelists
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdvangelists {

    String pubid;

    String placement;
}

