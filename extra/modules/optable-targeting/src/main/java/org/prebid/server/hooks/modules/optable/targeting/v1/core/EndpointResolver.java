package org.prebid.server.hooks.modules.optable.targeting.v1.core;

public class EndpointResolver {

    private static final String TENANT = "{TENANT}";

    private static final String ORIGIN = "{ORIGIN}";

    private EndpointResolver() {
    }

    public static String resolve(String endpointTemplate, String tenant, String origin) {
        return endpointTemplate.replace(TENANT, tenant)
                .replace(ORIGIN, origin);
    }
}
