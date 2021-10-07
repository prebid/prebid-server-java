package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Builder
@Value
public class DeliveryStatsProperties {

    @NonNull
    String endpoint;

    int cachedReportsNumber;

    long timeoutMs;

    int lineItemsPerReport;

    int reportsIntervalMs;

    int batchesIntervalMs;

    boolean requestCompressionEnabled;

    @NonNull
    String username;

    @NonNull
    String password;
}
