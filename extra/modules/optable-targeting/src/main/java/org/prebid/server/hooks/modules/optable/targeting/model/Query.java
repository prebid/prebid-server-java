package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value(staticConstructor = "of")
public class Query {

    String ids;

    String attributes;

    public String toQueryString() {
        if (StringUtils.isEmpty(ids) && !StringUtils.isEmpty(attributes)) {
            return attributes.substring(1);
        }

        return ids + attributes;
    }
}
