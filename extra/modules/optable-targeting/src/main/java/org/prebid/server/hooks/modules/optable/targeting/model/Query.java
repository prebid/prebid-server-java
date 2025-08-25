package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Query {

    String ids;

    String attributes;

    public String toQueryString() {
        return ids + attributes;
    }
}
