package org.prebid.server.proto.openrtb.ext.request.verizonmedia;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.verizonmedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpVerizonmedia {

    String dcn;

    String pos;
}
