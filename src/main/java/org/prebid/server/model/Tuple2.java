package org.prebid.server.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Tuple2<L, R> {

    L left;

    R right;
}
