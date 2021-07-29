package org.prebid.server.bidder.unicorn.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.unicorn.ExtImpUnicorn;

@AllArgsConstructor(staticName = "of")
@Value
@Builder(toBuilder = true)
public class UnicornImpExt {

    UnicornImpExtContext context;

    ExtImpUnicorn bidder;
}
