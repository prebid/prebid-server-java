package org.prebid.server.util;

import io.vertx.uritemplate.UriTemplate;

import java.util.List;

public class UriTemplateUtil {

    public static final UriTemplate ONLY_QUERY_URI_TEMPLATE = UriTemplate.of("{?queryParams*}");

    private UriTemplateUtil() {

    }

    public static UriTemplate createTemplate(String url, List<String> macros) {
        String resolvedUrl = url;
        for (String macro : macros) {
            resolvedUrl = resolvedUrl.replace("{{%s}}".formatted(macro), "{%s}".formatted(macro));
        }

        return UriTemplate.of(resolvedUrl);
    }

    public static UriTemplate createTemplate(String url, String... params) {
        return createTemplate(url, url.contains("?"), params);
    }

    public static UriTemplate createTemplate(String url, boolean hasQueryParameters, String... params) {
        final StringBuilder parametersTemplate = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            final String paramTemplate = i == 0 && !hasQueryParameters ? "{?%s*}" : "{&%s*}";
            parametersTemplate.append(paramTemplate.formatted(params[i]));
        }

        return UriTemplate.of(url + parametersTemplate);
    }

    public static UriTemplate createTemplate(String url, String param, List<String> macros) {
        return createTemplate(url, url.contains("?"), param, macros);
    }

    public static UriTemplate createTemplate(String url,
                                             boolean hasQueryParameters,
                                             String param,
                                             List<String> macros) {
        String resolvedUrl = url;
        for (String macro : macros) {
            resolvedUrl = resolvedUrl.replace("{{%s}}".formatted(macro), "{%s}".formatted(macro));
        }
        return createTemplate(resolvedUrl, hasQueryParameters, param);
    }

}
