package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import java.util.Objects;

public class APIClientFactory {

    private final APIClient apiClient;
    private final APIClient cachedAPIClient;

    private final boolean moduleCacheEnabled;

    public APIClientFactory(APIClient apiClient, APIClient cachedAPIClient, boolean moduleCacheEnabled) {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.cachedAPIClient = Objects.requireNonNull(cachedAPIClient);
        this.moduleCacheEnabled = moduleCacheEnabled;
    }

    public APIClient getClient(boolean isCacheEnabled) {
        return isCacheEnabled && moduleCacheEnabled ? cachedAPIClient : apiClient;
    }
}
