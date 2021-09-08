package org.prebid.server.deals.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AlertProxyProperties {

    boolean enabled;

    String url;

    int timeoutSec;

    Map<String, Long> alertTypes;

    String username;

    String password;
}
