package org.prebid.server.settings.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdUnitConfig {

    String id;

    String config;
}
