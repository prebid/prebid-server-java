package org.prebid.server.proto.openrtb.ext.request.yssp;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.yssp
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYssp {

    String dcn;

    String pos;
}
