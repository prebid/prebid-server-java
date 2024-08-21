package org.prebid.server.util.dsl.config.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.util.dsl.config.PrebidConfigParameter;

import java.util.Collections;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class SimpleDirectParameter implements PrebidConfigParameter.Direct {

    Iterable<String> values;

    public static SimpleDirectParameter of(String value) {
        return SimpleDirectParameter.of(Collections.singleton(value));
    }
}
