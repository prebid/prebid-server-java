package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Query {

    String ids;

    String hid;

    String attributes;

    String hidAttributes;

    public String toQueryString() {
        return ids + hid + attributes + hidAttributes;
    }
}
