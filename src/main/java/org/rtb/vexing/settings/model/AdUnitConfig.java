package org.rtb.vexing.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class AdUnitConfig {

    String id;

    String config;
}
