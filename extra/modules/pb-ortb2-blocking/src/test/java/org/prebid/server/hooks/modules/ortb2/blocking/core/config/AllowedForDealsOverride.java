package org.prebid.server.hooks.modules.ortb2.blocking.core.config;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AllowedForDealsOverride<T> {

    DealsConditions conditions;

    List<T> override;
}
