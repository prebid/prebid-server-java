package org.prebid.server.util;

import io.vertx.uritemplate.ExpandOptions;
import io.vertx.uritemplate.Variables;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

public class UriTemplate {

    private static final ExpandOptions REQUIRE_ALL_PARAMS = new ExpandOptions().setAllowVariableMiss(false);

    private static final String DYNAMIC_QUERY_PARAM = "DYNAMIC_QUERY_PARAM";
    private static final String DYNAMIC_QUERY_START_MACRO = "{?" + DYNAMIC_QUERY_PARAM + "*}";
    private static final String DYNAMIC_QUERY_CONTINUATION_MACRO = "{&" + DYNAMIC_QUERY_PARAM + "*}";

    private final io.vertx.uritemplate.UriTemplate template;

    private UriTemplate(String uri) {
        validateTemplate(Objects.requireNonNull(uri));
        this.template = io.vertx.uritemplate.UriTemplate.of(uri + chooseMacro(uri));
    }

    public static UriTemplate of(String uri) throws IllegalArgumentException {
        return new UriTemplate(uri);
    }

    private static void validateTemplate(String template) {
        final int queryStartIndex = template.indexOf('?');
        if (queryStartIndex != -1 && template.indexOf('}', queryStartIndex) != -1) {
            throw new IllegalArgumentException(UriTemplate.class.getName() + " does not support query variables.");
        }

        HttpUtil.validateUrl(template
                .replace("{", "")
                .replace("}", ""));
    }

    private static String chooseMacro(String template) {
        return template.contains("?")
                ? DYNAMIC_QUERY_CONTINUATION_MACRO
                : DYNAMIC_QUERY_START_MACRO;
    }

    public UriBuilder toBuilder() {
        return new UriBuilder(template);
    }

    @Override
    public String toString() throws NoSuchElementException {
        return toBuilder().build();
    }

    public static class UriBuilder {

        private final io.vertx.uritemplate.UriTemplate template;
        private final Variables variables;

        public UriBuilder(io.vertx.uritemplate.UriTemplate template) {
            this.template = template;
            this.variables = Variables.variables();
            variables.set(DYNAMIC_QUERY_PARAM, new LinkedHashMap<>());
        }

        public UriBuilder pathParam(String key, String value) {
            variables.set(key, value);
            return this;
        }

        public UriBuilder queryParam(String key, String value) {
            if (value != null) {
                variables.getMap(DYNAMIC_QUERY_PARAM).put(key, value);
            }
            return this;
        }

        public UriBuilder queryParam(String key, Collection<?> value) {
            if (value == null) {
                return this;
            }

            final String listAsString = value.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining(","));
            variables.getMap(DYNAMIC_QUERY_PARAM).put(key, listAsString);

            return this;
        }

        public String build() throws NoSuchElementException {
            return template.expandToString(variables, REQUIRE_ALL_PARAMS);
        }
    }
}
