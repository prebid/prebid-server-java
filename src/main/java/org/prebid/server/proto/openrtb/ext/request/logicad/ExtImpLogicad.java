package org.prebid.server.proto.openrtb.ext.request.logicad;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.logicad
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLogicad {

    String tid;
}
