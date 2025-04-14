package org.prebid.server.hooks.modules.optable.targeting.v1.core;

public class EndpointResolver {

    private static final String ACCOUNT_ID = "{ACCOUNT_ID}";

    private static final String ORIGIN = "{ORIGIN}";

    private EndpointResolver() {
    }

    public static String resolve(String endpointTemplate, String accountId, String origin) {
        return endpointTemplate.replace(ACCOUNT_ID, accountId)
                .replace(ORIGIN, origin);
    }
}
