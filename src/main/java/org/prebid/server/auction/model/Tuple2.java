package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Tuple2<L, R> {

    L left;

    R right;
}
