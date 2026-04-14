package org.prebid.server.util;

import io.vertx.uritemplate.ExpandOptions;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

public class Uri {

    private static final ExpandOptions REQUIRE_ALL_PARAMS = new ExpandOptions().setAllowVariableMiss(false);

    private static final String DYNAMIC_QUERY_PARAM = "DYNAMIC_QUERY_PARAM";
    private static final String DYNAMIC_QUERY_START_MACRO = "{?" + DYNAMIC_QUERY_PARAM + "*}";
    private static final String DYNAMIC_QUERY_CONTINUATION_MACRO = "{&" + DYNAMIC_QUERY_PARAM + "*}";

    private final UriTemplate template;

    private Uri(String uri) {
        validateTemplate(Objects.requireNonNull(uri));
        this.template = UriTemplate.of(uri + chooseMacro(uri));
    }

    public static Uri of(String uri) throws IllegalArgumentException {
        return new Uri(uri);
    }

    private static void validateTemplate(String template) {
        if (template.contains("{?")) {
            throw new IllegalArgumentException(
                    Uri.class.getName() + " does not support optional query variables.");
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

    public ParameterizedUri parameterized() {
        return new ParameterizedUri(template);
    }

    public ParameterizedUri replaceMacro(String key, String value) {
        return parameterized().replaceMacro(key, value);
    }

    public ParameterizedUri replaceMacro(String key, List<String> value) {
        return parameterized().replaceMacro(key, value);
    }

    public ParameterizedUri addQueryParam(String key, String value) {
        return parameterized().addQueryParam(key, value);
    }

    public ParameterizedUri addQueryParam(String key, Collection<?> value) {
        return parameterized().addQueryParam(key, value);
    }

    @Override
    public String toString() throws NoSuchElementException {
        return parameterized().expand();
    }

    public static class ParameterizedUri {

        private final UriTemplate template;
        private final Variables variables;

        private ParameterizedUri(UriTemplate template) {
            this.template = template;
            this.variables = Variables.variables();
            variables.set(DYNAMIC_QUERY_PARAM, new LinkedHashMap<>());
        }

        public ParameterizedUri replaceMacro(String key, String value) {
            variables.set(key, value);
            return this;
        }

        public ParameterizedUri replaceMacro(String key, List<String> value) {
            variables.set(key, value);
            return this;
        }

        public ParameterizedUri addQueryParam(String key, String value) {
            if (value != null) {
                variables.getMap(DYNAMIC_QUERY_PARAM).put(key, value);
            }
            return this;
        }

        public ParameterizedUri addQueryParam(String key, Collection<?> value) {
            if (value == null) {
                return this;
            }

            final String listAsString = value.stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining(","));
            variables.getMap(DYNAMIC_QUERY_PARAM).put(key, listAsString);

            return this;
        }

        public String expand() throws NoSuchElementException {
            return template.expandToString(variables, REQUIRE_ALL_PARAMS);
        }

        @Override
        public String toString() {
            return expand();
        }
    }
}
