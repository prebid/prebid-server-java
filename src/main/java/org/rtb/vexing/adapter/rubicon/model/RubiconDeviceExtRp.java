package org.rtb.vexing.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconDeviceExtRp {

    BigDecimal pixelratio;
}
