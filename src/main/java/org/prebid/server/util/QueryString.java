package org.prebid.server.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

public class QueryString {

    private static final Uri INTERNAL = new Uri(StringUtils.EMPTY);

    private final Uri.ParameterizedUri query;

    private QueryString() {
        query = INTERNAL.parameterized();
    }

    public static QueryString create() {
        return new QueryString();
    }

    public QueryString add(String key, String value) {
        query.addQueryParam(key, value);
        return this;
    }

    public QueryString add(String key, Collection<?> value) {
        query.addQueryParam(key, value);
        return this;
    }

    public String expand() {
        return query.expand();
    }
}
