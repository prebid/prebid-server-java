package org.prebid.server.bidder.gumgum.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigInteger;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpGumGumBanner {

    BigInteger slot;

    Integer maxw;

    Integer maxh;
}
