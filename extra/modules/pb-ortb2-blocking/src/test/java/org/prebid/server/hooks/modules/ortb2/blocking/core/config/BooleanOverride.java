package org.prebid.server.hooks.modules.ortb2.blocking.core.config;

import lombok.Value;

@Value(staticConstructor = "of")
public class BooleanOverride {

    Conditions conditions;

    Boolean override;
}
