package org.prebid.server.hooks.modules.rule.engine.core.rules.tree;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class LookupResult<T> {

    T value;

    List<String> matches;
}
