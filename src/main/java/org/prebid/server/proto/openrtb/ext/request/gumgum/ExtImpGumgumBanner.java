package org.prebid.server.proto.openrtb.ext.request.gumgum;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpGumgumBanner {

    BigInteger slot;

    Integer maxw;

    Integer maxh;
}
