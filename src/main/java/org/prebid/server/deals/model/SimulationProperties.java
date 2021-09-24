package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class SimulationProperties {

    boolean enabled;

    boolean winEventsEnabled;

    boolean userDetailsEnabled;
}
