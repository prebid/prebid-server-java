package org.prebid.server.util.dsl.config.impl;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;
import org.prebid.server.util.dsl.config.PrebidConfigParameters;

import java.util.List;

@Value(staticConstructor = "of")
public class SimpleParameters implements PrebidConfigParameters {

    @Getter(AccessLevel.NONE)
    List<PrebidConfigParameter> parameters;

    @Override
    public List<PrebidConfigParameter> get() {
        return parameters;
    }
}
