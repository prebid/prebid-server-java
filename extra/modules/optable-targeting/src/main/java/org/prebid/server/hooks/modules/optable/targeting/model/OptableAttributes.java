package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class OptableAttributes {

    private static final String REQUEST_SOURCE = "prebid-server";

    String gpp;

    Set<Integer> gppSid;

    String gdprConsent;

    boolean gdprApplies;

    List<String> ips;

    String userAgent;

    Long timeout;

    public String getRequestSource() {
        return REQUEST_SOURCE;
    }
}
