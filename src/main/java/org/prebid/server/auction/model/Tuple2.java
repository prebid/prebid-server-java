package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Tuple2<L, R> {

    L left;

    R right;
}
