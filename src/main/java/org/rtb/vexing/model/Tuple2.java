package org.rtb.vexing.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Tuple2<L, R> {

    L left;

    R right;
}
