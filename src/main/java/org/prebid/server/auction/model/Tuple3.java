package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Tuple3<L, M, R> {

    L left;

    M middle;

    R right;
}
