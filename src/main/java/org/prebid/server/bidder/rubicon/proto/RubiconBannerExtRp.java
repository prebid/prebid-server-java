package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconBannerExtRp {

    Integer sizeId;

    List<Integer> altSizeIds;

    String mime;
}
