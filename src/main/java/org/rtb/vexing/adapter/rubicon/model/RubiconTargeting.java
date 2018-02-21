package org.rtb.vexing.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconTargeting {

    String key;

    List<String> values;
}
