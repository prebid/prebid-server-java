package org.prebid.server.proto.openrtb.ext.request.engagebdr;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.engagebdr
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpEngagebdr {

    String sspid;
}
