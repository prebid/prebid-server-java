package org.prebid.server.bidder.appnexus.proto;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;

@Value(staticConstructor = "of")
public class ExtImp {

    ExtImpAppnexus bidder;

    String gpid;

}
