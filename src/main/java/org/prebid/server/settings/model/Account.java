package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Account {

    String id;

    String priceGranularity;

    Integer bannerCacheTtl;

    Integer videoCacheTtl;

    Boolean eventsEnabled;

    Boolean enforceGdpr;

    Integer analyticsSamplingFactor;
}

