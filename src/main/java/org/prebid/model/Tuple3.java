package org.prebid.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Tuple3<L, M, R> {

    L left;

    M middle;

    R right;
}
