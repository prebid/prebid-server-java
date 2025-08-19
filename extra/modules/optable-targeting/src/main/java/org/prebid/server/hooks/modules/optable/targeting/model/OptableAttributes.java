package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class OptableAttributes {

    String gpp;

    Set<Integer> gppSid;

    String gdprConsent;

    boolean gdprApplies;

    List<String> ips;

    Long timeout;
}
