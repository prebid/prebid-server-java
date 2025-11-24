package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config;

import lombok.Data;

import java.util.List;

@Data
public final class LiveIntentOmniChannelProperties {

    long requestTimeoutMs;

    String identityResolutionEndpoint;

    String authToken;

    float treatmentRate;

    List<String> targetBidders;
}
