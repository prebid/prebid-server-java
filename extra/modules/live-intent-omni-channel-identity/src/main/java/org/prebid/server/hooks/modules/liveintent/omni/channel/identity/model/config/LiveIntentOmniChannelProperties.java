package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config;

import lombok.Data;

@Data
public final class LiveIntentOmniChannelProperties {

    long requestTimeoutMs;

    String identityResolutionEndpoint;

    String authToken;

    float treatmentRate;
}
