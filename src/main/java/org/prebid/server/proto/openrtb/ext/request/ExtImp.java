package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext
 */
@Value(staticConstructor = "of")
public class ExtImp {

    ExtImpPrebid prebid;

    ExtImpContext context;
}
