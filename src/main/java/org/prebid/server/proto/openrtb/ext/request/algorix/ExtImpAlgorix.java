package org.prebid.server.proto.openrtb.ext.request.algorix;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Algorix Ext Imp
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAlgorix {

    String sid;

    String token;
}
