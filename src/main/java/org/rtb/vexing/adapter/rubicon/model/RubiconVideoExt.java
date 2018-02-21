package org.rtb.vexing.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconVideoExt {

    Integer skip;

    Integer skipdelay;

    RubiconVideoExtRp rp;
}
