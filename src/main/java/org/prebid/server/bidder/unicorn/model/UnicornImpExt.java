package org.prebid.server.bidder.unicorn.model;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.unicorn.ExtImpUnicorn;

@Value(staticConstructor = "of")
public class UnicornImpExt {

    UnicornImpExtContext context;

    ExtImpUnicorn bidder;
}
