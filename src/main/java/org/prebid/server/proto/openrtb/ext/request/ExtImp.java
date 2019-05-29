package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImp {

    ExtImpPrebid prebid;

    ExtImpContext context;
}
