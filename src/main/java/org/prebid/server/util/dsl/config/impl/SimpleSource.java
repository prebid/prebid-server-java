package org.prebid.server.util.dsl.config.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.util.dsl.config.PrebidConfigSource;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class SimpleSource implements PrebidConfigSource {

    String wildcard;

    String separator;

    Iterable<String> rules;
}
