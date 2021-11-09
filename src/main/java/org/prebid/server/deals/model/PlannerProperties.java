package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Builder
@Value
public class PlannerProperties {

    @NonNull
    String planEndpoint;

    @NonNull
    String registerEndpoint;

    long timeoutMs;

    long registerPeriodSeconds;

    @NonNull
    String username;

    @NonNull
    String password;
}
